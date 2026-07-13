package com.yowyob.tiibntick.core.agency.workforce.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.DelivererResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.DelivererR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.application.mapper.WorkforceMapper;
import com.yowyob.tiibntick.core.agency.workforce.domain.Deliverer;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.DelivererStatus;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.DelivererAvailabilityChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyDelivererService {

    private final DelivererR2dbcRepository delivererRepo;
    private final AgencyRegistryR2dbcRepository agencyRepo;
    private final AgencyEventPublisher eventPublisher;

    public Flux<DelivererResponse> listByAgency(UUID tenantId, UUID agencyId) {
        return requireAgency(agencyId, tenantId)
                .thenMany(delivererRepo.findByAgencyIdAndTenantId(agencyId, tenantId))
                .map(WorkforceMapper::toDelivererDomain)
                .map(WorkforceMapper::toDelivererResponse);
    }

    public Mono<DelivererResponse> getById(UUID tenantId, UUID delivererId) {
        return requireDeliverer(delivererId, tenantId).map(WorkforceMapper::toDelivererResponse);
    }

    @Transactional
    public Mono<DelivererResponse> register(RegisterInput input) {
        if (input.actorId() == null) {
            return Mono.error(new TntValidationException("actorId is required — actor creation is handled by BFF/tnt-actor-core"));
        }
        return requireAgency(input.agencyId(), input.tenantId())
                .then(Mono.defer(() -> {
                    Instant now = Instant.now();
                    Deliverer deliverer = Deliverer.register(
                            UUID.randomUUID(), input.tenantId(), input.agencyId(),
                            input.actorId(), input.phone(), now);
                    return delivererRepo.save(WorkforceMapper.toDelivererEntity(deliverer));
                }))
                .map(WorkforceMapper::toDelivererDomain)
                .map(WorkforceMapper::toDelivererResponse);
    }

    @Transactional
    public Mono<DelivererResponse> attachToBranch(UUID tenantId, UUID delivererId, UUID branchId) {
        return requireDelivererEntity(delivererId, tenantId)
                .flatMap(entity -> {
                    Deliverer d = WorkforceMapper.toDelivererDomain(entity);
                    d.attachToBranch(branchId, Instant.now());
                    return delivererRepo.save(WorkforceMapper.toDelivererEntity(d));
                })
                .map(WorkforceMapper::toDelivererDomain)
                .map(WorkforceMapper::toDelivererResponse);
    }

    @Transactional
    public Mono<DelivererResponse> suspend(UUID tenantId, UUID delivererId) {
        return mutate(delivererId, tenantId, d -> d.suspend(Instant.now()));
    }

    @Transactional
    public Mono<DelivererResponse> reactivate(UUID tenantId, UUID delivererId) {
        return mutate(delivererId, tenantId, d -> d.reactivate(Instant.now()));
    }

    @Transactional
    public Mono<DelivererResponse> reactivateByActorId(UUID tenantId, UUID actorId) {
        return delivererRepo.findByActorIdAndTenantId(actorId, tenantId)
                .flatMap(entity -> {
                    Deliverer d = WorkforceMapper.toDelivererDomain(entity);
                    try {
                        d.reactivate(Instant.now());
                    } catch (IllegalStateException ignored) {
                        return Mono.just(entity);
                    }
                    return delivererRepo.save(WorkforceMapper.toDelivererEntity(d));
                })
                .map(WorkforceMapper::toDelivererDomain)
                .map(WorkforceMapper::toDelivererResponse);
    }

    @Transactional
    public Mono<DelivererResponse> updateAvailability(UUID tenantId, UUID delivererId, DelivererStatus status) {
        Instant now = Instant.now();
        return requireDelivererEntity(delivererId, tenantId)
                .flatMap(entity -> {
                    Deliverer d = WorkforceMapper.toDelivererDomain(entity);
                    d.setAvailability(status, now);
                    return delivererRepo.save(WorkforceMapper.toDelivererEntity(d));
                })
                .map(WorkforceMapper::toDelivererDomain)
                .flatMap(d -> eventPublisher.publish(new DelivererAvailabilityChanged(
                                UUID.randomUUID(), delivererId, tenantId, d.getAgencyId(),
                                status != null ? status.name() : null, now, now))
                        .thenReturn(d))
                .map(WorkforceMapper::toDelivererResponse);
    }

    private Mono<DelivererResponse> mutate(UUID delivererId, UUID tenantId,
                                           java.util.function.Consumer<Deliverer> action) {
        return requireDelivererEntity(delivererId, tenantId)
                .flatMap(entity -> {
                    Deliverer d = WorkforceMapper.toDelivererDomain(entity);
                    action.accept(d);
                    return delivererRepo.save(WorkforceMapper.toDelivererEntity(d));
                })
                .map(WorkforceMapper::toDelivererDomain)
                .map(WorkforceMapper::toDelivererResponse);
    }

    private Mono<Void> requireAgency(UUID agencyId, UUID tenantId) {
        return agencyRepo.findByIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_NOT_FOUND", "Agency not found: " + agencyId)))
                .then();
    }

    private Mono<Deliverer> requireDeliverer(UUID delivererId, UUID tenantId) {
        return requireDelivererEntity(delivererId, tenantId).map(WorkforceMapper::toDelivererDomain);
    }

    private Mono<com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity.DelivererEntity>
    requireDelivererEntity(UUID delivererId, UUID tenantId) {
        return delivererRepo.findByIdAndTenantId(delivererId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "DELIVERER_NOT_FOUND", "Deliverer not found: " + delivererId)));
    }

    public record RegisterInput(UUID tenantId, UUID agencyId, UUID actorId, String phone) {}
}
