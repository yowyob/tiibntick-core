package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Subscription;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain SubscriptionRepository port to the
 * R2DBC reactive Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.SubscriptionRepository r2dbcRepository;

    @Override
    public Mono<Subscription> save(Subscription subscription) {
        return r2dbcRepository.save(subscription);
    }

    @Override
    public Mono<Subscription> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Mono<Subscription> findByFreelancerId(UUID freelancerId) {
        return r2dbcRepository.findByFreelancerId(freelancerId);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
