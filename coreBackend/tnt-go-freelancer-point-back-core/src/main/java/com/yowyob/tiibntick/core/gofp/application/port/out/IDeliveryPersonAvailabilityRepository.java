package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonAvailabilityEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IDeliveryPersonAvailabilityRepository {
    Mono<DeliveryPersonAvailabilityEntity> save(DeliveryPersonAvailabilityEntity entity);
    Mono<DeliveryPersonAvailabilityEntity> findByFreelancerActorId(UUID freelancerActorId);
    Flux<DeliveryPersonAvailabilityEntity> findAllAvailable();
}
