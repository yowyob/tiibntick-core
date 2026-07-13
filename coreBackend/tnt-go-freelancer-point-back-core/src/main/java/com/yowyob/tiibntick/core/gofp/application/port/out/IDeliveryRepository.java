package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IDeliveryRepository {
    Mono<DeliveryEntity> save(DeliveryEntity entity);
    Mono<DeliveryEntity> findById(UUID id);
    Mono<DeliveryEntity> findByAnnouncementId(UUID announcementId);
    Flux<DeliveryEntity> findByFreelancerActorId(UUID freelancerActorId);
    Flux<DeliveryEntity> findActiveByFreelancerActorId(UUID freelancerActorId);
    Flux<DeliveryEntity> findByStatus(String status);
    Mono<Void> deleteById(UUID id);
}
