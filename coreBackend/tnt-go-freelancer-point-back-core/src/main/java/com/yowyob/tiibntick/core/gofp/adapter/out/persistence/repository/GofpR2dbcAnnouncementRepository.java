package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface GofpR2dbcAnnouncementRepository
        extends ReactiveCrudRepository<AnnouncementEntity, UUID> {

    Flux<AnnouncementEntity> findByClientActorId(UUID clientActorId);

    Flux<AnnouncementEntity> findByStatus(String status);

    @Query("SELECT * FROM gofp.announcements WHERE status = :status ORDER BY created_at DESC LIMIT :limit")
    Flux<AnnouncementEntity> findByStatusOrderByCreatedAtDesc(String status, int limit);

    Mono<AnnouncementEntity> findByIdAndClientActorId(UUID id, UUID clientActorId);
}
