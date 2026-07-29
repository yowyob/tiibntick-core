package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Payment;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain PaymentRepository port to the R2DBC
 * Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.PaymentRepository r2dbcRepository;

    @Override
    public Mono<Payment> save(Payment payment) {
        return r2dbcRepository.save(payment);
    }

    @Override
    public Mono<Payment> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Mono<Payment> findByDeliveryId(UUID deliveryId) {
        return r2dbcRepository.findByDeliveryId(deliveryId);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
