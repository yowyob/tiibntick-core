package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface GofpR2dbcDeliveryRepository
        extends ReactiveCrudRepository<DeliveryEntity, UUID> {

    Mono<DeliveryEntity>  findByAnnouncementId(UUID announcementId);
    Flux<DeliveryEntity>  findByFreelancerActorId(UUID freelancerActorId);
    Flux<DeliveryEntity>  findByStatus(String status);

    @Query("SELECT * FROM gofp.deliveries WHERE freelancer_actor_id = :actorId AND status NOT IN ('DELIVERED','FAILED','CANCELLED')")
    Flux<DeliveryEntity>  findActiveByFreelancerActorId(UUID actorId);
}
