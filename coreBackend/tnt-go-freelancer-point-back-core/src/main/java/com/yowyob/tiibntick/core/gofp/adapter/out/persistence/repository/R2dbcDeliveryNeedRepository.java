package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryNeedEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface R2dbcDeliveryNeedRepository
        extends ReactiveCrudRepository<DeliveryNeedEntity, UUID> {

    Flux<DeliveryNeedEntity> findByClientActorId(UUID clientActorId);
    Flux<DeliveryNeedEntity> findByStatus(String status);
}
