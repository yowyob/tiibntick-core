package com.yowyob.tiibntick.core.actor.adapter.out.persistence;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.FreelancerProfileEntity;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository.FreelancerSpringDataRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC implementation of {@link IFreelancerRepository}.
 *
 * <h3> updates</h3>
 * <ul>
 *   <li>Entity ↔ domain mapping updated to handle {@code incidentHistoryCount}.</li>
 *   <li>Added {@link #findFirstByActorId} and {@link #incrementIncidentHistoryCount}.</li>
 * </ul>
 *
 * <h3> updates — FreelancerOrganization integration</h3>
 * <ul>
 *   <li>Entity ↔ domain mapping extended for {@code freelancerOrgId},
 *       {@code roleInOrg}, and {@code isOrgVerified} fields.</li>
 *   <li>Added {@link #findSubDeliverersByOrgId}, {@link #findOwnerByOrgId},
 *       {@link #findByActorIdAndOrgId}, {@link #updateOrgVerificationStatusForOrg}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
public class FreelancerR2dbcRepositoryAdapter implements IFreelancerRepository {

    private final FreelancerSpringDataRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public FreelancerR2dbcRepositoryAdapter(FreelancerSpringDataRepository repository,
                                            R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<Boolean> existsByActorId(UUID tenantId, UUID actorId) {
        return repository.existsByTenantIdAndActorId(tenantId, actorId);
    }

    @Override
    public Mono<FreelancerProfile> findById(UUID tenantId, UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<FreelancerProfile> findByActorId(UUID tenantId, UUID actorId) {
        return repository.findByTenantIdAndActorId(tenantId, actorId).map(this::toDomain);
    }

    @Override
    public Flux<FreelancerProfile> findActiveByServiceZone(UUID tenantId, UUID serviceZoneId) {
        return repository.findActiveByServiceZone(tenantId, serviceZoneId).map(this::toDomain);
    }

    @Override
    public Flux<FreelancerProfile> findByAssociatedAgency(UUID tenantId, UUID agencyId) {
        return repository.findByAssociatedAgency(tenantId, agencyId).map(this::toDomain);
    }

    @Override
    public Mono<FreelancerProfile> save(FreelancerProfile profile) {
        var entity = toEntity(profile);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .map(this::toDomain);
    }

    // ── tnt-incident-core integration ──────────────────────────────────────────

    @Override
    public Mono<FreelancerProfile> findFirstByActorId(UUID actorId) {
        return repository.findFirstByActorId(actorId).map(this::toDomain);
    }

    @Override
    public Mono<Void> incrementIncidentHistoryCount(UUID actorId, UUID tenantId) {
        return repository.incrementIncidentHistoryCount(actorId, tenantId);
    }

    // FreelancerOrganization integration ─────────────────────────────

    @Override
    public Flux<FreelancerProfile> findSubDeliverersByOrgId(UUID orgId) {
        return repository.findSubDeliverersByOrgId(orgId).map(this::toDomain);
    }

    @Override
    public Mono<FreelancerProfile> findOwnerByOrgId(UUID orgId) {
        return repository.findOwnerByOrgId(orgId).map(this::toDomain);
    }

    @Override
    public Mono<FreelancerProfile> findByActorIdAndOrgId(UUID actorId, UUID orgId) {
        return repository.findByActorIdAndFreelancerOrgId(actorId, orgId).map(this::toDomain);
    }

    @Override
    public Mono<Void> updateOrgVerificationStatusForOrg(UUID orgId, boolean verified) {
        return repository.updateOrgVerificationStatusForOrg(orgId, verified);
    }

    // ── Mapping helpers ────────────────────────────────────────────────────────

    /**
     * Maps a {@link FreelancerProfile} domain aggregate to a
     * {@link FreelancerProfileEntity} for persistence.
     * Includes the  org link fields.
     *
     * @param p the domain profile
     * @return the R2DBC entity
     */
    private FreelancerProfileEntity toEntity(FreelancerProfile p) {
        return new FreelancerProfileEntity(
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
                ActorJsonMapper.serviceZoneIdsToJson(p.serviceZoneIds()),
                ActorJsonMapper.availabilitySlotsToJson(p.availabilitySlots()),
                p.pricingPolicyId(),
                ActorJsonMapper.associatedAgencyIdsToJson(p.associatedAgencyIds()),
                p.incidentHistoryCount(),
                //  — org link fields
                p.freelancerOrgId(),
                p.roleInOrg() != null ? p.roleInOrg().name() : null,
                p.isOrgVerified());
    }

    /**
     * Maps a {@link FreelancerProfileEntity} to a {@link FreelancerProfile} domain aggregate.
     * Handles null org link fields gracefully (backward-compatible with pre- rows).
     *
     * @param e the R2DBC entity from the database
     * @return the reconstituted domain aggregate
     */
    private FreelancerProfile toDomain(FreelancerProfileEntity e) {
        return FreelancerProfile.rehydrate(
                e.id(), e.tenantId(), e.actorId(),
                e.actorStatus(), e.kycStatus(),
                e.locationLat(), e.locationLng(), e.locationAccuracy(),
                e.locationTimestamp(), e.locationSource(),
                e.ratingScore(), e.ratingTotal(), e.ratingUpdatedAt(),
                ActorJsonMapper.badgesFromJson(e.badgesJson()),
                e.createdAt(), e.updatedAt(),
                ActorJsonMapper.serviceZoneIdsFromJson(e.serviceZoneIdsJson()),
                ActorJsonMapper.availabilitySlotsFromJson(e.availabilitySlotsJson()),
                e.pricingPolicyId(),
                ActorJsonMapper.associatedAgencyIdsFromJson(e.associatedAgencyIdsJson()),
                e.incidentHistoryCount(),
                //  — org link fields (nullable — safe for old rows)
                e.freelancerOrgId(),
                e.roleInOrg(),
                e.isOrgVerified());
    }
}
