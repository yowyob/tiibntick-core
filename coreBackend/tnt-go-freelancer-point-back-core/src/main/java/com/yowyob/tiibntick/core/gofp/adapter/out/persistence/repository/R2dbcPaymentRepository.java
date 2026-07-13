package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcPaymentRepository
        extends ReactiveCrudRepository<PaymentEntity, UUID> {

    Mono<PaymentEntity> findByDeliveryId(UUID deliveryId);
    Flux<PaymentEntity> findByFreelancerActorId(UUID freelancerActorId);
}
