package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IDeliveryUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IGofpEventPublisher;
import com.yowyob.tiibntick.core.gofp.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofp.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.gofp.domain.exception.InvalidDeliveryStateException;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.DeliveryStatus;
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
public class DeliveryCoreService implements IDeliveryUseCase {

    private final IDeliveryRepository     deliveryRepository;
    private final IAnnouncementRepository announcementRepository;
    private final IGofpEventPublisher     eventPublisher;

    @Override
    public Mono<DeliveryEntity> createDelivery(UUID announcementId, UUID freelancerActorId) {
        DeliveryEntity delivery = DeliveryEntity.builder()
            .id(UUID.randomUUID())
            .announcementId(announcementId)
            .freelancerActorId(freelancerActorId)
            .status(DeliveryStatus.CREATED.name())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        return deliveryRepository.save(delivery)
            .flatMap(saved -> announcementRepository.findById(announcementId)
                .flatMap(ann -> {
                    ann.setStatus(AnnouncementStatus.IN_PROGRESS.name());
                    ann.setUpdatedAt(Instant.now());
                    return announcementRepository.save(ann);
                })
                .thenReturn(saved));
    }

    @Override
    public Mono<DeliveryEntity> confirmPickup(UUID deliveryId) {
        return transition(deliveryId, DeliveryStatus.PICKED_UP, DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED);
    }

    @Override
    public Mono<DeliveryEntity> startTransit(UUID deliveryId) {
        return transition(deliveryId, DeliveryStatus.IN_TRANSIT, DeliveryStatus.PICKED_UP, DeliveryStatus.AT_RELAY);
    }

    @Override
    public Mono<DeliveryEntity> depositAtRelay(UUID deliveryId, UUID relayHubId) {
        return findOrThrow(deliveryId).flatMap(d -> {
            d.setStatus(DeliveryStatus.AT_RELAY.name());
            d.setRelayHubId(relayHubId);
            d.setUpdatedAt(Instant.now());
            return deliveryRepository.save(d);
        });
    }

    @Override
    public Mono<DeliveryEntity> resumeFromRelay(UUID deliveryId) {
        return transition(deliveryId, DeliveryStatus.IN_TRANSIT, DeliveryStatus.AT_RELAY);
    }

    @Override
    // Chantier C · Audit n°3 · P5: the delivery save and the outbox envelope written by
    // IGofpEventPublisher must commit atomically.
    @Transactional
    public Mono<DeliveryEntity> completeDelivery(UUID deliveryId, Double lat, Double lon) {
        return findOrThrow(deliveryId).flatMap(d -> {
            d.setStatus(DeliveryStatus.DELIVERED.name());
            d.setActualDeliveryTime(Instant.now());
            d.setDeliveryLat(lat);
            d.setDeliveryLon(lon);
            d.setUpdatedAt(Instant.now());
            return deliveryRepository.save(d);
        }).flatMap(d -> eventPublisher
            .publishDeliveryCompleted(d.getId(), d.getFreelancerActorId(),
                // clientActorId comes from announcement — pass null here, event handler resolves it
                null)
            .thenReturn(d));
    }

    @Override
    public Mono<DeliveryEntity> failDelivery(UUID deliveryId, String reason) {
        return findOrThrow(deliveryId).flatMap(d -> {
            d.setStatus(DeliveryStatus.FAILED.name());
            d.setUpdatedAt(Instant.now());
            return deliveryRepository.save(d);
        });
    }

    @Override
    public Mono<DeliveryEntity> cancelDelivery(UUID deliveryId) {
        return findOrThrow(deliveryId).flatMap(d -> {
            d.setStatus(DeliveryStatus.CANCELLED.name());
            d.setUpdatedAt(Instant.now());
            return deliveryRepository.save(d);
        });
    }

    @Override
    public Mono<Void> updateLocation(UUID deliveryId, double lat, double lon) {
        return findOrThrow(deliveryId).flatMap(d -> {
            d.setDeliveryLat(lat);
            d.setDeliveryLon(lon);
            d.setUpdatedAt(Instant.now());
            return deliveryRepository.save(d);
        }).then();
    }

    @Override
    public Mono<DeliveryEntity> findById(UUID id)                              { return findOrThrow(id); }
    @Override
    public Flux<DeliveryEntity> findByFreelancerActorId(UUID freelancerActorId){ return deliveryRepository.findByFreelancerActorId(freelancerActorId); }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Mono<DeliveryEntity> findOrThrow(UUID id) {
        return deliveryRepository.findById(id)
            .switchIfEmpty(Mono.error(new DeliveryNotFoundException(id)));
    }

    /** Transitions l'état seulement si le statut courant est l'un des états attendus. */
    private Mono<DeliveryEntity> transition(UUID deliveryId, DeliveryStatus target, DeliveryStatus... allowed) {
        return findOrThrow(deliveryId).flatMap(d -> {
            DeliveryStatus current = DeliveryStatus.fromValue(d.getStatus());
            for (DeliveryStatus a : allowed) {
                if (current == a) {
                    d.setStatus(target.name());
                    d.setUpdatedAt(Instant.now());
                    return deliveryRepository.save(d);
                }
            }
            return Mono.error(new InvalidDeliveryStateException(current, target));
        });
    }
}
