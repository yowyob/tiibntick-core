package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Reactive R2DBC repository for Announcement entity.
 */
public interface AnnouncementRepository extends ReactiveCrudRepository<Announcement, UUID> {

    Flux<Announcement> findAllByClientId(UUID clientId);

    Flux<Announcement> findAllByStatus(AnnouncementStatus status);

    Flux<Announcement> findAllByAssignedFreelancerId(UUID assignedFreelancerId);
}
