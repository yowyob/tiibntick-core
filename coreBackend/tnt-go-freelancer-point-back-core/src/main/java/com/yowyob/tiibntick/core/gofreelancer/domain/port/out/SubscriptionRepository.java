package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Subscription;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for subscription persistence operations.
 */
public interface SubscriptionRepository {

    Mono<Subscription> save(Subscription subscription);

    Mono<Subscription> findById(UUID id);

    /**
     * Finds the active subscription for a given delivery person.
     * Used to determine their quota and commission rate at delivery time.
     *
     * @param freelancerId the delivery person's UUID
     * @return the subscription, or empty if none exists
     */
    Mono<Subscription> findByFreelancerId(UUID freelancerId);

    Mono<Void> deleteById(UUID id);
}
