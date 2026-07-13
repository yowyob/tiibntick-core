package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementSubscriptionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcAnnouncementSubscriptionRepository
        extends ReactiveCrudRepository<AnnouncementSubscriptionEntity, UUID> {

    Flux<AnnouncementSubscriptionEntity> findByAnnouncementId(UUID announcementId);
    Flux<AnnouncementSubscriptionEntity> findByFreelancerActorId(UUID freelancerActorId);
    Mono<AnnouncementSubscriptionEntity> findByAnnouncementIdAndFreelancerActorId(UUID announcementId, UUID freelancerActorId);

    @Query("SELECT * FROM gofp.announcement_subscriptions WHERE announcement_id = :announcementId AND status = :status")
    Flux<AnnouncementSubscriptionEntity> findByAnnouncementIdAndStatus(UUID announcementId, String status);
}
