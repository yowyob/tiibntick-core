package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.AnnouncementSubscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for announcement subscription persistence operations.
 */
public interface AnnouncementSubscriptionRepository {

    Mono<AnnouncementSubscription> save(AnnouncementSubscription subscription);

    Mono<AnnouncementSubscription> findByAnnouncementIdAndFreelancerId(UUID announcementId, UUID freelancerId);

    Flux<AnnouncementSubscription> findAllByAnnouncementId(UUID announcementId);

    Flux<AnnouncementSubscription> findAllByFreelancerId(UUID freelancerId);
}
