package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PaymentEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IPaymentRepository {
    Mono<PaymentEntity> save(PaymentEntity entity);
    Mono<PaymentEntity> findById(UUID id);
    Mono<PaymentEntity> findByDeliveryId(UUID deliveryId);
    Flux<PaymentEntity> findByFreelancerActorId(UUID freelancerActorId);
}
