package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryAnnouncementEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.ParcelEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.AnnouncementPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.ParcelPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcAnnouncementResponseRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcParcelRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

/**
 * Persistence adapter for {@code DeliveryAnnouncementRepository}.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class DeliveryAnnouncementRepositoryAdapter implements DeliveryAnnouncementRepository {

    private final R2dbcAnnouncementRepository announcementRepo;
    private final R2dbcAnnouncementResponseRepository responseRepo;
    private final R2dbcParcelRepository parcelRepo;
    private final ParcelWriter parcelWriter;

    @Override
    public Mono<DeliveryAnnouncement> save(DeliveryAnnouncement announcement) {
        // Lazy: the entity is built at subscription, after any save chained before it (versions up to date).
        return Mono.defer(() -> doSave(announcement));
    }

    private Mono<DeliveryAnnouncement> doSave(DeliveryAnnouncement announcement) {
        var entity = AnnouncementPersistenceMapper.toEntity(announcement);

        // Save parcel first (single write path, version copied back), then announcement, then all responses
        return parcelWriter.saveIfPending(announcement.getParcel())
                .then(announcementRepo.existsById(entity.getId()))
                .flatMap(annExists -> {
                    entity.setNew(!annExists);
                    return announcementRepo.save(entity);
                })
                .flatMap(saved -> {
                    var responseEntities = announcement.getResponses().stream()
                            .map(AnnouncementPersistenceMapper::responseToEntity)
                            .toList();
                    if (responseEntities.isEmpty()) {
                        return Mono.just(saved);
                    }
                    return reactor.core.publisher.Flux.fromIterable(responseEntities)
                            .flatMap(re -> responseRepo.existsById(re.getId())
                                    .flatMap(exists -> {
                                        re.setNew(!exists);
                                        return responseRepo.save(re);
                                    }))
                            .then(Mono.just(saved));
                })
                .map(saved -> {
                    // lot evt-l2: return the same aggregate (its recorded events are kept for publication)
                    announcement.markPersisted(saved.getVersion());
                    return announcement;
                });
    }

    @Override
    public Mono<UUID> findTenantIdOf(UUID announcementId) {
        return announcementRepo.findById(announcementId).map(e -> e.getTenantId());
    }

    @Override
    public Mono<DeliveryAnnouncement> findById(UUID tenantId, UUID announcementId) {
        return announcementRepo.findByTenantIdAndId(tenantId, announcementId)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .flatMap(pe -> responseRepo.findByAnnouncementId(e.getId()).collectList()
                                .map(responses -> AnnouncementPersistenceMapper.toDomain(e,
                                        ParcelPersistenceMapper.toDomain(pe),
                                        responses.stream()
                                                .map(AnnouncementPersistenceMapper::responseToDomain)
                                                .toList()))));
    }

    @Override
    public Mono<Boolean> hasResponse(UUID announcementId, UUID freelancerId) {
        return responseRepo.existsByAnnouncementIdAndDeliveryPersonId(announcementId, freelancerId)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<DeliveryAnnouncement> findByOrigin(UUID tenantId, String originType, UUID originId) {
        return announcementRepo.findByOrigin(tenantId, originType, originId)
                .flatMap(this::hydrateFromEntity);
    }

    @Override
    public Flux<DeliveryAnnouncement> findByOrigins(UUID tenantId, String originType,
                                                    Collection<UUID> originIds) {
        return announcementRepo.findByOrigins(tenantId, originType, originIds)
                .flatMap(this::hydrateFromEntity);
    }

    @Override
    public Flux<DeliveryAnnouncement> findRespondedBy(UUID tenantId, Collection<UUID> deliveryPersonIds) {
        return announcementRepo.findRespondedBy(tenantId, deliveryPersonIds)
                .concatMap(this::hydrateFromEntity);
    }

    @Override
    public Flux<DeliveryAnnouncement> findByClientId(UUID tenantId, UUID clientId) {
        return announcementRepo.findByTenantIdAndClientId(tenantId, clientId)
                .flatMap(e -> hydrateFromEntity(e));
    }

    @Override
    public Flux<DeliveryAnnouncement> findByStatus(UUID tenantId, AnnouncementStatus status) {
        return announcementRepo.findByTenantIdAndStatus(tenantId, status.name())
                .flatMap(e -> hydrateFromEntity(e));
    }

    @Override
    public Flux<DeliveryAnnouncement> findOpenAnnouncements(UUID tenantId) {
        return announcementRepo.findOpenByTenantId(tenantId)
                .flatMap(e -> hydrateFromEntity(e));
    }

    @Override
    public Flux<DeliveryAnnouncement> findOpenAnnouncementsNear(UUID tenantId,
            com.yowyob.tiibntick.core.delivery.application.port.in.query.NearbyAnnouncementQuery query) {
        boolean hasPoint = query.hasPoint();
        double lat = hasPoint ? query.latitude() : 0.0;
        double lng = hasPoint ? query.longitude() : 0.0;
        // Boîte englobante du rayon (1° de latitude ≈ 111,2 km) : préfiltre avant la distance exacte.
        double dLat = query.radiusKm() / 111.2;
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double dLng = query.radiusKm() / (111.2 * cosLat);
        return announcementRepo.findOpenNear(tenantId, hasPoint, lat, lng,
                        lat - dLat, lat + dLat, lng - dLng, lng + dLng,
                        query.radiusKm(), query.normalizedCity())
                .flatMap(e -> hydrateFromEntity(e));
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Mono<DeliveryAnnouncement> hydrateFromEntity(
            DeliveryAnnouncementEntity e) {
        return parcelRepo.findById(e.getParcelId())
                .flatMap(pe -> responseRepo.findByAnnouncementId(e.getId()).collectList()
                        .map(responses -> AnnouncementPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe),
                                responses.stream()
                                        .map(AnnouncementPersistenceMapper::responseToDomain)
                                        .toList())));
    }
}
