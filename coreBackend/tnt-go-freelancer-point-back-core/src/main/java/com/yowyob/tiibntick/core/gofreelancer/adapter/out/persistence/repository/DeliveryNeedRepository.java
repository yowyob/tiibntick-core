package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Reactive R2DBC repository for DeliveryNeed entity.
 */
public interface DeliveryNeedRepository extends ReactiveCrudRepository<DeliveryNeed, UUID> {

    Flux<DeliveryNeed> findAllByUserId(UUID userId);

    Flux<DeliveryNeed> findAllByTargetRelayPointIdIn(java.util.Collection<UUID> targetRelayPointIds);
}
