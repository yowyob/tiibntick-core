package com.yowyob.tiibntick.core.actor.adapter.out.persistence;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.DelivererProfileEntity;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository.DelivererSpringDataRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC implementation of {@link IDelivererRepository}.
 *
 * <p> — Updated to support tnt-auth-core and tnt-incident-core integration:
 * <ul>
 *   <li>{@link #findActorIdByUserId} — delegates to Spring Data query</li>
 *   <li>{@link #findAgencyIdByActorId} — delegates to Spring Data query</li>
 *   <li>{@link #findFirstByActorId} — cross-tenant lookup for reputation port</li>
 *   <li>{@link #incrementIncidentHistoryCount} — atomic counter update</li>
 *   <li>Entity ↔ domain mapping updated to handle {@code incidentHistoryCount}
 *       and {@code fraudFlaggedByIncidentId} fields.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
public class DelivererR2dbcRepositoryAdapter implements IDelivererRepository {

    private final DelivererSpringDataRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public DelivererR2dbcRepositoryAdapter(DelivererSpringDataRepository repository,
                                           R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<Boolean> existsByActorId(UUID tenantId, UUID actorId) {
        return repository.existsByTenantIdAndActorId(tenantId, actorId);
    }

    @Override
    public Mono<DelivererProfile> findById(UUID tenantId, UUID id) {
        return repository.findByIdAndTenantId(id, tenantId).map(this::toDomain);
    }

    @Override
    public Mono<DelivererProfile> findByActorId(UUID tenantId, UUID actorId) {
        return repository.findByTenantIdAndActorId(tenantId, actorId).map(this::toDomain);
    }

    @Override
    public Flux<DelivererProfile> findByAgencyId(UUID tenantId, UUID agencyId) {
        return repository.findAllByTenantIdAndAgencyId(tenantId, agencyId).map(this::toDomain);
    }

    @Override
    public Flux<DelivererProfile> findByBranchId(UUID tenantId, UUID branchId) {
        return repository.findAllByTenantIdAndBranchId(tenantId, branchId).map(this::toDomain);
    }

    @Override
    public Flux<DelivererProfile> findAvailableByBranchId(UUID tenantId, UUID branchId) {
        return repository.findAvailableByTenantIdAndBranchId(tenantId, branchId)
                .map(this::toDomain);
    }

    @Override
    public Flux<DelivererProfile> findAvailableNear(UUID tenantId, double latitude,
                                                     double longitude, double radiusKm,
                                                     double minCapacityKg) {
        return repository.findAvailableNear(tenantId, latitude, longitude, radiusKm, minCapacityKg)
                .map(this::toDomain);
    }

    @Override
    public Mono<DelivererProfile> save(DelivererProfile profile) {
        var entity = toEntity(profile);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .map(this::toDomain);
    }

    // ── tnt-auth-core integration ──────────────────────────────────────────────

    @Override
    public Mono<UUID> findActorIdByUserId(UUID userId, UUID tenantId) {
        return repository.findActorIdByUserId(userId, tenantId);
    }

    @Override
    public Mono<UUID> findAgencyIdByActorId(UUID actorId, UUID tenantId) {
        return repository.findAgencyIdByActorId(actorId, tenantId);
    }

    // ── tnt-incident-core integration ──────────────────────────────────────────

    @Override
    public Mono<DelivererProfile> findFirstByActorId(UUID actorId) {
        return repository.findFirstByActorId(actorId).map(this::toDomain);
    }

    @Override
    public Mono<Void> incrementIncidentHistoryCount(UUID actorId, UUID tenantId) {
        return repository.incrementIncidentHistoryCount(actorId, tenantId);
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private DelivererProfileEntity toEntity(DelivererProfile p) {
        return new DelivererProfileEntity(
                p.id(), p.tenantId(), p.actorId(),
                p.actorStatus().name(), p.kycStatus().name(),
                p.hasLocation() ? p.currentLocation().latitude() : null,
                p.hasLocation() ? p.currentLocation().longitude() : null,
                p.hasLocation() ? p.currentLocation().accuracy() : null,
                p.hasLocation() ? p.currentLocation().timestamp() : null,
                p.hasLocation() ? p.currentLocation().source().name() : null,
                p.rating().score(), p.rating().totalRatings(), p.rating().lastUpdatedAt(),
                ActorJsonMapper.badgesToJson(p.badges()),
                p.createdAt(), p.updatedAt(),
                p.agencyId(), p.branchId(), p.vehicleId(), p.missionActiveId(),
                p.capacityKg(), p.contractId(), p.delivererType().name(),
                p.incidentHistoryCount(), p.fraudFlaggedByIncidentId(), p.blockchainDid());
    }

    private DelivererProfile toDomain(DelivererProfileEntity e) {
        return DelivererProfile.rehydrate(
                e.id(), e.tenantId(), e.actorId(),
                e.actorStatus(), e.kycStatus(),
                e.locationLat(), e.locationLng(), e.locationAccuracy(),
                e.locationTimestamp(), e.locationSource(),
                e.ratingScore(), e.ratingTotal(), e.ratingUpdatedAt(),
                ActorJsonMapper.badgesFromJson(e.badgesJson()),
                e.createdAt(), e.updatedAt(),
                e.agencyId(), e.branchId(), e.vehicleId(), e.missionActiveId(),
                e.capacityKg(), e.contractId(), e.delivererType(),
                e.incidentHistoryCount(), e.fraudFlaggedByIncidentId(), e.blockchainDid());
    }
}
