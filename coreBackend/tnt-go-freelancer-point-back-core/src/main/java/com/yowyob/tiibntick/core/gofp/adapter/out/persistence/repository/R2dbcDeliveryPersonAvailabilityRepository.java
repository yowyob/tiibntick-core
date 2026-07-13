package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryPersonAvailabilityEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcDeliveryPersonAvailabilityRepository
        extends ReactiveCrudRepository<DeliveryPersonAvailabilityEntity, UUID> {

    Mono<DeliveryPersonAvailabilityEntity> findByFreelancerActorId(UUID freelancerActorId);

    @Query("SELECT * FROM gofp.delivery_person_availability WHERE is_available = true AND current_lat IS NOT NULL")
    Flux<DeliveryPersonAvailabilityEntity> findAllAvailable();
}
