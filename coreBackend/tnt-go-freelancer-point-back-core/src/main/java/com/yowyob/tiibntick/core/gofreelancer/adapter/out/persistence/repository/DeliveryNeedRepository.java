package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Reactive R2DBC repository for DeliveryNeed entity.
 */
public interface DeliveryNeedRepository extends ReactiveCrudRepository<DeliveryNeed, UUID> {

    Flux<DeliveryNeed> findAllByUserId(UUID userId);

    Flux<DeliveryNeed> findAllByStatus(DeliveryNeedStatus status);
}
