package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonPricingEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IDeliveryPersonPricingRepository {
    Mono<DeliveryPersonPricingEntity> save(DeliveryPersonPricingEntity entity);
    Mono<DeliveryPersonPricingEntity> findByFreelancerActorId(UUID freelancerActorId);
}
