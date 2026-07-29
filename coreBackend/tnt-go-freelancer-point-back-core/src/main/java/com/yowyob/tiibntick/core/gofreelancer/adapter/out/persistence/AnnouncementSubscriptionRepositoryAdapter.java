package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.AnnouncementSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.AnnouncementSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain AnnouncementSubscriptionRepository port
 * to the R2DBC Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class AnnouncementSubscriptionRepositoryAdapter implements AnnouncementSubscriptionRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AnnouncementSubscriptionRepository r2dbcRepository;

    @Override
    public Mono<AnnouncementSubscription> save(AnnouncementSubscription subscription) {
        return r2dbcRepository.save(subscription);
    }

    @Override
    public Mono<AnnouncementSubscription> findByAnnouncementIdAndFreelancerId(
            UUID announcementId, UUID freelancerId) {
        return r2dbcRepository.findByAnnouncementIdAndFreelancerId(announcementId, freelancerId);
    }

    @Override
    public Flux<AnnouncementSubscription> findAllByAnnouncementId(UUID announcementId) {
        return r2dbcRepository.findAllByAnnouncementId(announcementId);
    }

    @Override
    public Flux<AnnouncementSubscription> findAllByFreelancerId(UUID freelancerId) {
        return r2dbcRepository.findAllByFreelancerId(freelancerId);
    }
}
