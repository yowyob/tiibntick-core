package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.AnnouncementSubscription;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Repository for managing AnnouncementSubscription entities.
 * Provides reactive CRUD operations to check and save subscriptions.
 *
 * @author François-Charles ATANGA
 * @date 04/02/2026
 */
@Repository
public interface AnnouncementSubscriptionRepository extends ReactiveCrudRepository<AnnouncementSubscription, UUID> {
    reactor.core.publisher.Mono<AnnouncementSubscription> findByAnnouncementIdAndFreelancerId(UUID announcementId,
            UUID freelancerId);

    Flux<AnnouncementSubscription> findAllByAnnouncementId(UUID announcementId);

    Flux<AnnouncementSubscription> findAllByFreelancerId(UUID freelancerId);
}
