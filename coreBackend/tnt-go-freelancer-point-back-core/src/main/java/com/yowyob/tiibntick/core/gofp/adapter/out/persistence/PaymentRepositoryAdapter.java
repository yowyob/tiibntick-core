package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PaymentEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcPaymentRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements IPaymentRepository {

    private final R2dbcPaymentRepository r2dbc;

    @Override public Mono<PaymentEntity> save(PaymentEntity e)                          { return r2dbc.save(e); }
    @Override public Mono<PaymentEntity> findById(UUID id)                               { return r2dbc.findById(id); }
    @Override public Mono<PaymentEntity> findByDeliveryId(UUID deliveryId)               { return r2dbc.findByDeliveryId(deliveryId); }
    @Override public Flux<PaymentEntity> findByFreelancerActorId(UUID freelancerActorId) { return r2dbc.findByFreelancerActorId(freelancerActorId); }
}
