package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PacketEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IAnnouncementUseCase {

    /** Crée une annonce avec son colis. Si autoPublish=true, publie immédiatement. */
    Mono<AnnouncementEntity> createAnnouncement(AnnouncementEntity announcement, PacketEntity packet);

    /** Publie une annonce DRAFT → PUBLISHED (déclenche matching). */
    Mono<AnnouncementEntity> publishAnnouncement(UUID announcementId);

    /** Assigne un livreur à une annonce (PUBLISHED → ASSIGNED). */
    Mono<AnnouncementEntity> assignFreelancer(UUID announcementId, UUID freelancerActorId);

    /** Annule une annonce. */
    Mono<AnnouncementEntity> cancelAnnouncement(UUID announcementId);

    Mono<AnnouncementEntity>  findById(UUID id);
    Flux<AnnouncementEntity>  findByClientActorId(UUID clientActorId);
    Flux<AnnouncementEntity>  findPublished();
}
