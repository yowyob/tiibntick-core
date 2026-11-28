package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryNeedStateService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges IDeliveryNeedRepository to the R2DBC Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class DeliveryNeedRepositoryAdapter implements IDeliveryNeedRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryNeedRepository r2dbcRepository;
    private final DeliveryNeedStateService stateService;

    /**
     * The status, delivery and courier of a need are never stored: every need leaving this adapter has
     * them derived from delivery-core.
     */
    @Override
    public Mono<DeliveryNeed> save(DeliveryNeed deliveryNeed) {
        return r2dbcRepository.save(deliveryNeed).flatMap(stateService::hydrate);
    }

    @Override
    public Mono<DeliveryNeed> findById(UUID id) {
        return r2dbcRepository.findById(id).flatMap(stateService::hydrate);
    }

    @Override
    public Flux<DeliveryNeed> findAll() {
        return stateService.hydrateAll(r2dbcRepository.findAll());
    }

    @Override
    public Flux<DeliveryNeed> findAllByUserId(UUID userId) {
        return stateService.hydrateAll(r2dbcRepository.findAllByUserId(userId));
    }

    /** Filters on the derived status, so every need is read then derived (no stored column to query). */
    @Override
    public Flux<DeliveryNeed> findAllByStatus(DeliveryNeedStatus status) {
        return stateService.hydrateAll(r2dbcRepository.findAll()).filter(n -> n.getStatus() == status);
    }

    @Override
    public Flux<DeliveryNeed> findAllByTargetRelayPointIdIn(java.util.Collection<UUID> relayPointIds) {
        return stateService.hydrateAll(r2dbcRepository.findAllByTargetRelayPointIdIn(relayPointIds));
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
