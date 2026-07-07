package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.AnnouncementResponseEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code AnnouncementResponseEntity}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcAnnouncementResponseRepository
        extends ReactiveCrudRepository<AnnouncementResponseEntity, UUID> {

    @Query("SELECT * FROM tnt_announcement_responses WHERE announcement_id = :announcementId ORDER BY created_at ASC")
    Flux<AnnouncementResponseEntity> findByAnnouncementId(UUID announcementId);
}
