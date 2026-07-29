package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Payment;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for Payment entity.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
public interface PaymentRepository extends ReactiveCrudRepository<Payment, UUID> {

    /**
     * Finds a payment by its associated delivery id.
     *
     * @param deliveryId the delivery UUID
     * @return the matching payment, or empty if none exists
     */
    Mono<Payment> findByDeliveryId(UUID deliveryId);
}