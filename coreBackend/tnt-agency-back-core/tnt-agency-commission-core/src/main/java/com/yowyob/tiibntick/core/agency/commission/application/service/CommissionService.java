package com.yowyob.tiibntick.core.agency.commission.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.commission.adapter.in.web.dto.CommissionResponse;
import com.yowyob.tiibntick.core.agency.commission.adapter.out.clients.WalletCorePort;
import com.yowyob.tiibntick.core.agency.commission.adapter.out.persistence.CommissionRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.commission.application.mapper.CommissionMapper;
import com.yowyob.tiibntick.core.agency.commission.domain.CommissionRecord;
import com.yowyob.tiibntick.core.agency.commission.domain.vo.CommissionStatus;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.DelivererR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Commission lifecycle — payout orchestrated via billing-wallet (platform Core).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionService {

    private final CommissionRecordR2dbcRepository commissionRepo;
    private final AgencyRegistryR2dbcRepository agencyRepo;
    private final DelivererR2dbcRepository delivererRepo;
    private final WalletCorePort walletCore;

    public Flux<CommissionResponse> listByAgency(UUID tenantId, UUID agencyId) {
        return commissionRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .map(CommissionMapper::toDomain)
                .map(CommissionMapper::toResponse);
    }

    public Flux<CommissionResponse> listByDeliverer(UUID tenantId, UUID delivererId) {
        return commissionRepo.findByDelivererIdAndTenantId(delivererId, tenantId)
                .map(CommissionMapper::toDomain)
                .map(CommissionMapper::toResponse);
    }

    public Mono<CommissionResponse> getById(UUID tenantId, UUID commissionId) {
        return requireCommission(commissionId, tenantId).map(CommissionMapper::toResponse);
    }

    @Transactional
    public Mono<CommissionResponse> create(CreateInput input) {
        return requireAgency(input.agencyId(), input.tenantId())
                .then(requireDeliverer(input.delivererId(), input.tenantId()))
                .then(Mono.defer(() -> {
                    Instant now = Instant.now();
                    String currency = input.currency() != null ? input.currency() : "XAF";
                    CommissionRecord record = CommissionRecord.create(
                            UUID.randomUUID(), input.tenantId(), input.agencyId(),
                            input.delivererId(), input.missionId(),
                            input.amount(), currency, now);
                    return commissionRepo.save(CommissionMapper.toEntity(record));
                }))
                .map(CommissionMapper::toDomain)
                .map(CommissionMapper::toResponse);
    }

    @Transactional
    public Mono<CommissionResponse> validate(UUID tenantId, UUID commissionId) {
        return mutate(commissionId, tenantId, c -> c.validate(Instant.now()));
    }

    /**
     * Marks commission PAID and triggers Mobile Money payout via billing-wallet.
     */
    @Transactional
    public Mono<CommissionResponse> pay(UUID tenantId, UUID commissionId) {
        return requireCommissionEntity(commissionId, tenantId)
                .flatMap(entity -> {
                    CommissionRecord record = CommissionMapper.toDomain(entity);
                    if (record.getStatus() != CommissionStatus.VALIDATED) {
                        return Mono.error(new IllegalStateException(
                                "Commission must be VALIDATED before payment (current: "
                                        + record.getStatus() + ")"));
                    }
                    record.pay(Instant.now());
                    return commissionRepo.save(CommissionMapper.toEntity(record))
                            .map(CommissionMapper::toDomain);
                })
                .flatMap(saved -> delivererRepo.findByIdAndTenantId(saved.getDelivererId(), tenantId)
                        .flatMap(deliverer -> walletCore.pay(new WalletCorePort.PaymentRequest(
                                saved.getTenantId(),
                                deliverer.getActorId(),
                                saved.getId(),
                                deliverer.getPhone(),
                                saved.getAmount(),
                                saved.getCurrency(),
                                "Commission mission " + saved.getMissionId(),
                                UUID.randomUUID().toString()
                        )).thenReturn(saved)))
                .map(CommissionMapper::toResponse);
    }

    @Transactional
    public Mono<CommissionResponse> dispute(UUID tenantId, UUID commissionId, String reason) {
        return mutate(commissionId, tenantId, c -> c.dispute(reason, Instant.now()));
    }

    @Transactional
    public Mono<Void> confirmWalletPayment(UUID tenantId, UUID commissionId) {
        return requireCommissionEntity(commissionId, tenantId)
                .flatMap(entity -> {
                    CommissionRecord record = CommissionMapper.toDomain(entity);
                    if (record.getStatus() == CommissionStatus.PAID) {
                        return Mono.empty();
                    }
                    if (record.getStatus() == CommissionStatus.VALIDATED) {
                        record.pay(Instant.now());
                        return commissionRepo.save(CommissionMapper.toEntity(record)).then();
                    }
                    log.debug("Wallet confirmation ignored commissionId={} status={}",
                            commissionId, record.getStatus());
                    return Mono.empty();
                });
    }

    private Mono<CommissionResponse> mutate(UUID commissionId, UUID tenantId,
                                            java.util.function.Consumer<CommissionRecord> action) {
        return requireCommissionEntity(commissionId, tenantId)
                .flatMap(entity -> {
                    CommissionRecord record = CommissionMapper.toDomain(entity);
                    action.accept(record);
                    return commissionRepo.save(CommissionMapper.toEntity(record));
                })
                .map(CommissionMapper::toDomain)
                .map(CommissionMapper::toResponse);
    }

    private Mono<CommissionRecord> requireCommission(UUID commissionId, UUID tenantId) {
        return requireCommissionEntity(commissionId, tenantId).map(CommissionMapper::toDomain);
    }

    private Mono<com.yowyob.tiibntick.core.agency.commission.adapter.out.persistence.entity.CommissionRecordEntity>
    requireCommissionEntity(UUID commissionId, UUID tenantId) {
        return commissionRepo.findByIdAndTenantId(commissionId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "COMMISSION_NOT_FOUND", "Commission not found: " + commissionId)));
    }

    private Mono<Void> requireAgency(UUID agencyId, UUID tenantId) {
        return agencyRepo.findByIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_NOT_FOUND", "Agency not found: " + agencyId)))
                .then();
    }

    private Mono<Void> requireDeliverer(UUID delivererId, UUID tenantId) {
        return delivererRepo.findByIdAndTenantId(delivererId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "DELIVERER_NOT_FOUND", "Deliverer not found: " + delivererId)))
                .then();
    }

    public record CreateInput(
            UUID tenantId, UUID agencyId, UUID delivererId, UUID missionId,
            BigDecimal amount, String currency) {}
}
