package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outgoing port — persistence for DeliveryNeed (implemented by R2DBC adapter).
 */
public interface IDeliveryNeedRepository {

    Mono<DeliveryNeed> save(DeliveryNeed deliveryNeed);

    Mono<DeliveryNeed> findById(UUID id);

    Flux<DeliveryNeed> findAllByUserId(UUID userId);

    Flux<DeliveryNeed> findAllByStatus(DeliveryNeedStatus status);

    Mono<Void> deleteById(UUID id);
}
