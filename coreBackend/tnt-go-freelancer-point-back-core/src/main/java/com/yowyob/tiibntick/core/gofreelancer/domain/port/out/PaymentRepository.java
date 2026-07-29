package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Payment;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for payment persistence operations.
 */
public interface PaymentRepository {

    Mono<Payment> save(Payment payment);

    Mono<Payment> findById(UUID id);

    /**
     * Finds a payment by its associated delivery id.
     * Used to retrieve commission breakdown for a completed delivery.
     *
     * @param deliveryId the delivery UUID
     * @return the matching payment, or empty if none exists
     */
    Mono<Payment> findByDeliveryId(UUID deliveryId);

    Mono<Void> deleteById(UUID id);
}
