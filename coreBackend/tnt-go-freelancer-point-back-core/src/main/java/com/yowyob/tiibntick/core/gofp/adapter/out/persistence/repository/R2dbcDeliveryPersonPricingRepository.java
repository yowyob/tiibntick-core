package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonPricingEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcDeliveryPersonPricingRepository
        extends ReactiveCrudRepository<DeliveryPersonPricingEntity, UUID> {

    Mono<DeliveryPersonPricingEntity> findByFreelancerActorId(UUID freelancerActorId);
}
