package com.yowyob.tiibntick.core.agency.workforce.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.ContractResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.ContractR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.application.mapper.WorkforceMapper;
import com.yowyob.tiibntick.core.agency.workforce.domain.Contract;
import com.yowyob.tiibntick.core.agency.workforce.domain.support.CommissionRateNormalizer;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.ContractType;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.RemunerationModel;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.ContractSigned;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractR2dbcRepository contractRepo;
    private final AgencyEventPublisher eventPublisher;

    public Flux<ContractResponse> listByAgency(UUID tenantId, UUID agencyId) {
        return contractRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .map(WorkforceMapper::toContractDomain)
                .map(WorkforceMapper::toContractResponse);
    }

    public Flux<ContractResponse> listByDeliverer(UUID tenantId, UUID delivererId) {
        return contractRepo.findByDelivererIdAndTenantId(delivererId, tenantId)
                .map(WorkforceMapper::toContractDomain)
                .map(WorkforceMapper::toContractResponse);
    }

    public Mono<ContractResponse> getActiveByDeliverer(UUID tenantId, UUID delivererId) {
        return contractRepo.findActiveByDelivererIdAndTenantId(delivererId, tenantId)
                .map(WorkforceMapper::toContractDomain)
                .map(WorkforceMapper::toContractResponse)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "CONTRACT_NOT_FOUND", "No active contract for deliverer: " + delivererId)));
    }

    @Transactional
    public Mono<ContractResponse> sign(SignInput input) {
        Instant now = Instant.now();
        UUID contractId = UUID.randomUUID();
        Contract contract = Contract.sign(
                contractId, input.tenantId(), input.agencyId(), input.delivererId(),
                input.contractType(), input.startDate(), input.endDate(),
                input.remunerationModel(), input.baseSalary(),
                CommissionRateNormalizer.toPercentPoints(input.commissionRate()), now);
        return contractRepo.save(WorkforceMapper.toContractEntity(contract))
                .map(WorkforceMapper::toContractDomain)
                .flatMap(saved -> eventPublisher.publish(new ContractSigned(
                                UUID.randomUUID(), contractId, input.tenantId(), input.agencyId(),
                                input.delivererId(),
                                input.contractType() != null ? input.contractType().name() : null, now))
                        .thenReturn(saved))
                .map(WorkforceMapper::toContractResponse);
    }

    @Transactional
    public Mono<ContractResponse> terminate(UUID tenantId, UUID delivererId) {
        return contractRepo.findActiveByDelivererIdAndTenantId(delivererId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "CONTRACT_NOT_FOUND", "No active contract for deliverer: " + delivererId)))
                .flatMap(entity -> {
                    Contract contract = WorkforceMapper.toContractDomain(entity);
                    contract.terminate(Instant.now());
                    return contractRepo.save(WorkforceMapper.toContractEntity(contract));
                })
                .map(WorkforceMapper::toContractDomain)
                .map(WorkforceMapper::toContractResponse);
    }

    @Transactional
    public Mono<ContractResponse> updateRemuneration(UUID tenantId, UUID contractId,
                                                     BigDecimal baseSalary, BigDecimal commissionRate) {
        return requireContract(contractId, tenantId)
                .flatMap(contract -> {
                    contract.updateRemuneration(baseSalary,
                            CommissionRateNormalizer.toPercentPoints(commissionRate), Instant.now());
                    return contractRepo.save(WorkforceMapper.toContractEntity(contract));
                })
                .map(WorkforceMapper::toContractDomain)
                .map(WorkforceMapper::toContractResponse);
    }

    @Transactional
    public Mono<ContractResponse> renew(UUID tenantId, UUID contractId, LocalDate endDate) {
        return requireContract(contractId, tenantId)
                .flatMap(contract -> {
                    contract.renew(endDate, Instant.now());
                    return contractRepo.save(WorkforceMapper.toContractEntity(contract));
                })
                .map(WorkforceMapper::toContractDomain)
                .map(WorkforceMapper::toContractResponse);
    }

    private Mono<Contract> requireContract(UUID contractId, UUID tenantId) {
        return contractRepo.findByIdAndTenantId(contractId, tenantId)
                .map(WorkforceMapper::toContractDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "CONTRACT_NOT_FOUND", "Contract not found: " + contractId)));
    }

    public record SignInput(
            UUID tenantId, UUID agencyId, UUID delivererId,
            ContractType contractType, LocalDate startDate, LocalDate endDate,
            RemunerationModel remunerationModel, BigDecimal baseSalary, BigDecimal commissionRate) {}
}
