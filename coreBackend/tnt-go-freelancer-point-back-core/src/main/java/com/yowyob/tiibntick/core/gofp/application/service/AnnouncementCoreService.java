package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PacketEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IAnnouncementUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IGofpEventPublisher;
import com.yowyob.tiibntick.core.gofp.application.port.out.IPacketRepository;
import com.yowyob.tiibntick.core.gofp.domain.exception.AnnouncementNotFoundException;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.AnnouncementStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementCoreService implements IAnnouncementUseCase {

    private final IAnnouncementRepository announcementRepository;
    private final IPacketRepository       packetRepository;
    private final IGofpEventPublisher     eventPublisher;

    @Override
    // Chantier C · Audit n°3 · P5: packet/announcement saves and the outbox envelope
    // written by IGofpEventPublisher must commit atomically.
    @Transactional
    public Mono<AnnouncementEntity> createAnnouncement(AnnouncementEntity announcement, PacketEntity packet) {
        packet.setId(UUID.randomUUID());
        packet.setCreatedAt(Instant.now());
        packet.setUpdatedAt(Instant.now());

        return packetRepository.save(packet)
            .flatMap(savedPacket -> {
                announcement.setId(UUID.randomUUID());
                announcement.setPacketId(savedPacket.getId());
                announcement.setCreatedAt(Instant.now());
                announcement.setUpdatedAt(Instant.now());

                boolean autoPublish = Boolean.TRUE.equals(announcement.getAutoPublish());
                announcement.setStatus(autoPublish
                    ? AnnouncementStatus.PUBLISHED.name()
                    : AnnouncementStatus.DRAFT.name());

                return announcementRepository.save(announcement)
                    .flatMap(saved -> {
                        if (autoPublish) {
                            return eventPublisher
                                .publishAnnouncementPublished(saved.getId(), saved.getClientActorId())
                                .thenReturn(saved);
                        }
                        return Mono.just(saved);
                    });
            });
    }

    @Override
    @Transactional
    public Mono<AnnouncementEntity> publishAnnouncement(UUID announcementId) {
        return announcementRepository.findById(announcementId)
            .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(announcementId)))
            .flatMap(ann -> {
                ann.setStatus(AnnouncementStatus.PUBLISHED.name());
                ann.setUpdatedAt(Instant.now());
                return announcementRepository.save(ann);
            })
            .flatMap(ann -> eventPublisher
                .publishAnnouncementPublished(ann.getId(), ann.getClientActorId())
                .thenReturn(ann));
    }

    @Override
    public Mono<AnnouncementEntity> assignFreelancer(UUID announcementId, UUID freelancerActorId) {
        return announcementRepository.findById(announcementId)
            .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(announcementId)))
            .flatMap(ann -> {
                ann.setStatus(AnnouncementStatus.ASSIGNED.name());
                ann.setUpdatedAt(Instant.now());
                return announcementRepository.save(ann);
            });
    }

    @Override
    public Mono<AnnouncementEntity> cancelAnnouncement(UUID announcementId) {
        return announcementRepository.findById(announcementId)
            .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(announcementId)))
            .flatMap(ann -> {
                ann.setStatus(AnnouncementStatus.CANCELLED.name());
                ann.setUpdatedAt(Instant.now());
                return announcementRepository.save(ann);
            });
    }

    @Override
    public Mono<AnnouncementEntity>  findById(UUID id)                    { return announcementRepository.findById(id).switchIfEmpty(Mono.error(new AnnouncementNotFoundException(id))); }
    @Override
    public Flux<AnnouncementEntity>  findByClientActorId(UUID clientActorId) { return announcementRepository.findByClientActorId(clientActorId); }
    @Override
    public Flux<AnnouncementEntity>  findPublished()                       { return announcementRepository.findByStatus(AnnouncementStatus.PUBLISHED.name()); }
}
