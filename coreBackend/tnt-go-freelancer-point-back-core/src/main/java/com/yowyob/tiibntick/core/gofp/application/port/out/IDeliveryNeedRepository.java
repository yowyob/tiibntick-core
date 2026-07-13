package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryNeedEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IDeliveryNeedRepository {
    Mono<DeliveryNeedEntity> save(DeliveryNeedEntity entity);
    Mono<DeliveryNeedEntity> findById(UUID id);
    Flux<DeliveryNeedEntity> findByClientActorId(UUID clientActorId);
    Flux<DeliveryNeedEntity> findByStatus(String status);
}
