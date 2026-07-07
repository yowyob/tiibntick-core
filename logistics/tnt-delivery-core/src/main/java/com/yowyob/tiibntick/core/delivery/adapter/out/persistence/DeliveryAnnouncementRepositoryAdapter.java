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

    @Override
    public Mono<DeliveryAnnouncement> save(DeliveryAnnouncement announcement) {
        var parcelEntity = ParcelPersistenceMapper.toEntity(announcement.getParcel());
        var entity = AnnouncementPersistenceMapper.toEntity(announcement);

        // Save parcel first, then announcement, then all responses
        return parcelRepo.existsById(parcelEntity.getId())
                .flatMap(parcelExists -> {
                    parcelEntity.setNew(!parcelExists);
                    return parcelRepo.save(parcelEntity);
                })
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
                .map(saved -> hydrate(saved, parcelEntity, announcement));
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

    private DeliveryAnnouncement hydrate(
            DeliveryAnnouncementEntity saved,
            ParcelEntity parcelEntity,
            DeliveryAnnouncement announcement) {
        return AnnouncementPersistenceMapper.toDomain(saved,
                ParcelPersistenceMapper.toDomain(parcelEntity),
                announcement.getResponses());
    }
}
