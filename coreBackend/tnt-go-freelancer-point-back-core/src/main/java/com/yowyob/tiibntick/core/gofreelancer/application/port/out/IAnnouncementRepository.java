package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outgoing port — persistence for Announcement (implemented by R2DBC adapter).
 */
public interface IAnnouncementRepository {

    Mono<Announcement> save(Announcement announcement);

    Mono<Announcement> findById(UUID id);

    Flux<Announcement> findAll();

    Flux<Announcement> findAllByClientId(UUID clientId);

    Flux<Announcement> findAllByStatus(AnnouncementStatus status);

    Flux<Announcement> findAllByAssignedFreelancerId(UUID freelancerId);

    Mono<Void> deleteById(UUID id);
}
