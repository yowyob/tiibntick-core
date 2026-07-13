package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryNeedEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IDeliveryNeedUseCase {

    Mono<DeliveryNeedEntity> createDeliveryNeed(DeliveryNeedEntity need);
    Mono<DeliveryNeedEntity> assignDelivery(UUID needId, UUID deliveryId);
    Mono<DeliveryNeedEntity> cancel(UUID needId);
    Mono<DeliveryNeedEntity> findById(UUID id);
    Flux<DeliveryNeedEntity> findByClientActorId(UUID clientActorId);
    Flux<DeliveryNeedEntity> findOpen();
}
