package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IAnnouncementRepository {
    Mono<AnnouncementEntity> save(AnnouncementEntity entity);
    Mono<AnnouncementEntity> findById(UUID id);
    Flux<AnnouncementEntity> findByClientActorId(UUID clientActorId);
    Flux<AnnouncementEntity> findByStatus(String status);
    Mono<Void> deleteById(UUID id);
}
