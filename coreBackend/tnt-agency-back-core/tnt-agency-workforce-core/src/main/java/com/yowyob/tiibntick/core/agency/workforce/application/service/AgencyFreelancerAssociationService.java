package com.yowyob.tiibntick.core.agency.workforce.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.FreelancerAssociationResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.FreelancerAssociationR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.application.mapper.WorkforceMapper;
import com.yowyob.tiibntick.core.agency.workforce.domain.FreelancerAssociation;
import com.yowyob.tiibntick.core.agency.workforce.domain.support.CommissionRateNormalizer;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.FreelancerAssociated;
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
public class AgencyFreelancerAssociationService {

    private final FreelancerAssociationR2dbcRepository associationRepo;
    private final AgencyRegistryR2dbcRepository agencyRepo;
    private final AgencyEventPublisher eventPublisher;

    public Flux<FreelancerAssociationResponse> listByAgency(UUID tenantId, UUID agencyId) {
        return associationRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .map(WorkforceMapper::toAssociationDomain)
                .map(WorkforceMapper::toAssociationResponse);
    }

    @Transactional
    public Mono<FreelancerAssociationResponse> associate(AssociateInput input) {
        return requireAgency(input.agencyId(), input.tenantId())
                .then(Mono.defer(() -> {
                    Instant now = Instant.now();
                    UUID associationId = UUID.randomUUID();
                    FreelancerAssociation association = FreelancerAssociation.create(
                            associationId, input.tenantId(), input.agencyId(),
                            input.freelancerActorId(),
                            CommissionRateNormalizer.toPercentPoints(input.commissionRate()),
                            input.startDate(), now);
                    return associationRepo.save(WorkforceMapper.toAssociationEntity(association))
                            .flatMap(entity -> eventPublisher.publish(new FreelancerAssociated(
                                            UUID.randomUUID(), associationId, input.tenantId(),
                                            input.agencyId(), input.freelancerActorId(), now))
                                    .thenReturn(entity));
                }))
                .map(WorkforceMapper::toAssociationDomain)
                .map(WorkforceMapper::toAssociationResponse);
    }

    @Transactional
    public Mono<FreelancerAssociationResponse> end(UUID tenantId, UUID associationId, LocalDate endDate) {
        return requireAssociation(associationId, tenantId)
                .flatMap(association -> {
                    association.end(endDate, Instant.now());
                    return associationRepo.save(WorkforceMapper.toAssociationEntity(association));
                })
                .map(WorkforceMapper::toAssociationDomain)
                .map(WorkforceMapper::toAssociationResponse);
    }

    @Transactional
    public Mono<FreelancerAssociationResponse> pause(UUID tenantId, UUID associationId) {
        return requireAssociation(associationId, tenantId)
                .flatMap(association -> {
                    association.pause(Instant.now());
                    return associationRepo.save(WorkforceMapper.toAssociationEntity(association));
                })
                .map(WorkforceMapper::toAssociationDomain)
                .map(WorkforceMapper::toAssociationResponse);
    }

    @Transactional
    public Mono<FreelancerAssociationResponse> cancelInvitation(UUID tenantId, UUID associationId) {
        return requireAssociation(associationId, tenantId)
                .flatMap(association -> {
                    association.cancelInvitation(Instant.now());
                    return associationRepo.save(WorkforceMapper.toAssociationEntity(association));
                })
                .map(WorkforceMapper::toAssociationDomain)
                .map(WorkforceMapper::toAssociationResponse);
    }

    private Mono<Void> requireAgency(UUID agencyId, UUID tenantId) {
        return agencyRepo.findByIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_NOT_FOUND", "Agency not found: " + agencyId)))
                .then();
    }

    private Mono<FreelancerAssociation> requireAssociation(UUID associationId, UUID tenantId) {
        return associationRepo.findByIdAndTenantId(associationId, tenantId)
                .map(WorkforceMapper::toAssociationDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "ASSOCIATION_NOT_FOUND", "Freelancer association not found: " + associationId)));
    }

    public record AssociateInput(
            UUID tenantId, UUID agencyId, UUID freelancerActorId,
            BigDecimal commissionRate, LocalDate startDate) {}
}
