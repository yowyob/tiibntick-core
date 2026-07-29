package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Subscription;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for Subscription entity operations.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
@Repository
public interface SubscriptionRepository extends ReactiveCrudRepository<Subscription, UUID> {

    /**
     * Finds the subscription linked to a given delivery person.
     * Used at delivery completion time to resolve commission rate and quota.
     */
    Mono<Subscription> findByFreelancerId(UUID freelancerId);
}
