package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementSubscriptionEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IAnnouncementSubscriptionRepository {
    Mono<AnnouncementSubscriptionEntity> save(AnnouncementSubscriptionEntity entity);
    Mono<AnnouncementSubscriptionEntity> findById(UUID id);
    Flux<AnnouncementSubscriptionEntity> findByAnnouncementId(UUID announcementId);
    Flux<AnnouncementSubscriptionEntity> findByFreelancerActorId(UUID freelancerActorId);
    Mono<AnnouncementSubscriptionEntity> findByAnnouncementIdAndFreelancerActorId(UUID announcementId, UUID freelancerActorId);
    Flux<AnnouncementSubscriptionEntity> findByAnnouncementIdAndStatus(UUID announcementId, String status);
}
