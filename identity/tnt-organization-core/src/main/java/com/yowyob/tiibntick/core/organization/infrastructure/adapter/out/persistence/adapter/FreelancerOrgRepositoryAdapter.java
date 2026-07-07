package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgRepositoryPort;
import com.yowyob.tiibntick.core.organization.domain.enums.AssociationStatus;
import com.yowyob.tiibntick.core.organization.domain.enums.DeliveryZoneType;
import com.yowyob.tiibntick.core.organization.domain.enums.FreelancerRegStatus;
import com.yowyob.tiibntick.core.organization.domain.enums.KycLevel;
import com.yowyob.tiibntick.core.organization.domain.enums.ZoneAccessDifficulty;
import com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization;
import com.yowyob.tiibntick.core.organization.domain.vo.AssociatedDelivererRef;
import com.yowyob.tiibntick.core.organization.domain.vo.FreelancerBillingProfile;
import com.yowyob.tiibntick.core.organization.domain.vo.FreelancerCapabilities;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.FreelancerOperationalZoneEntity;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.FreelancerOrgEntity;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.FreelancerSubDelivererEntity;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository.FreelancerOperationalZoneR2dbcRepository;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository.FreelancerOrgR2dbcRepository;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository.FreelancerSubDelivererR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence adapter implementing {@link FreelancerOrgRepositoryPort} using Spring Data R2DBC.
 *
 * <p>Handles bidirectional mapping between the {@link FreelancerOrganization} aggregate
 * and three R2DBC entities:
 * <ul>
 *   <li>{@link FreelancerOrgEntity} — main table {@code tnt_freelancer_organization}</li>
 *   <li>{@link FreelancerSubDelivererEntity} — table {@code tnt_freelancer_sub_deliverer}</li>
 *   <li>{@link FreelancerOperationalZoneEntity} — table {@code tnt_freelancer_operational_zone}</li>
 * </ul>
 *
 * <p>Save strategy: the adapter uses a delete-then-insert pattern for zones and
 * sub-deliverers to avoid complex diff logic. This is safe because both collections
 * are small (max 5 sub-deliverers, typically &lt; 10 zones).
 *
 * @author MANFOUO Braun
 */
@Component
public class FreelancerOrgRepositoryAdapter implements FreelancerOrgRepositoryPort {

    private final FreelancerOrgR2dbcRepository orgRepo;
    private final FreelancerSubDelivererR2dbcRepository subDelivererRepo;
    private final FreelancerOperationalZoneR2dbcRepository zoneRepo;

    /**
     * Constructor injection.
     *
     * @param orgRepo          main FreelancerOrg repository
     * @param subDelivererRepo sub-deliverer associations repository
     * @param zoneRepo         operational zones repository
     */
    public FreelancerOrgRepositoryAdapter(FreelancerOrgR2dbcRepository orgRepo,
                                           FreelancerSubDelivererR2dbcRepository subDelivererRepo,
                                           FreelancerOperationalZoneR2dbcRepository zoneRepo) {
        this.orgRepo = orgRepo;
        this.subDelivererRepo = subDelivererRepo;
        this.zoneRepo = zoneRepo;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<FreelancerOrganization> save(FreelancerOrganization org) {
        UUID orgUuid = org.getId().value();

        return orgRepo.existsById(orgUuid)
                .flatMap(exists -> {
                    FreelancerOrgEntity entity = toEntity(org);
                    entity.setNew(!exists);
                    return orgRepo.save(entity);
                })
                // Delete and re-insert sub-deliverers
                .flatMap(saved -> subDelivererRepo.deleteByFreelancerOrgId(orgUuid)
                        .thenMany(Flux.fromIterable(org.getSubDeliverers())
                                .map(ref -> toSubDelivererEntity(ref, orgUuid))
                                .flatMap(subDelivererRepo::save))
                        .then()
                        // Delete and re-insert zones
                        .then(zoneRepo.deleteByFreelancerOrgId(orgUuid))
                        .thenMany(Flux.fromIterable(org.getOperationalZones())
                                .map(zone -> toZoneEntity(zone, orgUuid))
                                .flatMap(zoneRepo::save))
                        .then()
                        .thenReturn(saved))
                .flatMap(saved -> loadFullAggregate(saved));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<FreelancerOrganization> findById(OrganizationId id) {
        return orgRepo.findById(id.value())
                .flatMap(this::loadFullAggregate);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<FreelancerOrganization> findByOwnerActorId(UUID ownerActorId) {
        return orgRepo.findFirstByOwnerActorIdOrderByCreatedAtDesc(ownerActorId)
                .flatMap(this::loadFullAggregate);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<FreelancerOrganization> findByTenantId(String tenantId) {
        return orgRepo.findByTenantId(tenantId)
                .flatMap(this::loadFullAggregate);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<FreelancerOrganization> findByZoneProximity(double latitude,
                                                             double longitude,
                                                             double radiusKm) {
        // Convert km to degrees (approx 1 degree = 111 km)
        double radiusDeg = radiusKm / 111.0;
        return zoneRepo.findOrgIdsByProximity(longitude, latitude, radiusDeg)
                .flatMap(orgUuid -> orgRepo.findById(orgUuid))
                .flatMap(this::loadFullAggregate);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> existsById(OrganizationId id) {
        return orgRepo.existsById(id.value());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> existsByTradeName(String tradeName) {
        return orgRepo.existsByTradeNameIgnoreCase(tradeName);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<AssociatedDelivererRef> findSubDeliverersByOrgId(OrganizationId orgId) {
        return subDelivererRepo.findByFreelancerOrgId(orgId.value())
                .map(e -> toSubDelivererRef(e, orgId));
    }

    // ─── Aggregate loading helper ─────────────────────────────────────────────

    /**
     * Loads the full {@link FreelancerOrganization} aggregate from the main entity
     * by also fetching zones and sub-deliverers from their respective tables.
     *
     * @param entity the main FreelancerOrgEntity already loaded from the DB
     * @return a {@link Mono} emitting the fully assembled aggregate
     */
    private Mono<FreelancerOrganization> loadFullAggregate(FreelancerOrgEntity entity) {
        UUID orgUuid = entity.getId();
        OrganizationId orgId = OrganizationId.of(orgUuid);

        Mono<List<ServiceZone>> zonesMono = zoneRepo.findByFreelancerOrgId(orgUuid)
                .map(this::toServiceZone)
                .collectList();

        Mono<List<AssociatedDelivererRef>> subsMono = subDelivererRepo.findByFreelancerOrgId(orgUuid)
                .map(e -> toSubDelivererRef(e, orgId))
                .collectList();

        return Mono.zip(zonesMono, subsMono)
                .map(tuple -> toDomain(entity, tuple.getT1(), tuple.getT2()));
    }

    // ─── Mapping helpers — Domain → Entity ───────────────────────────────────

    private FreelancerOrgEntity toEntity(FreelancerOrganization org) {
        FreelancerCapabilities caps = org.getCapabilities();
        FreelancerBillingProfile billing = org.getBillingProfile();
        return FreelancerOrgEntity.builder()
                .id(org.getId().value())
                .organizationId(org.getOrganizationId())
                .tenantId(org.getTenantId())
                .tradeName(org.getTradeName())
                .ownerActorId(org.getOwnerActorId())
                .registrationStatus(org.getRegistrationStatus().name())
                .kycLevel(org.getKycLevel().name())
                .activePolicyId(billing.activePolicyId())
                .defaultTemplateCode(billing.defaultTemplateCode())
                .vatApplicable(billing.vatApplicable())
                .taxId(billing.taxId())
                .trustScore(org.getTrustScore())
                .blockchainDid(org.getBlockchainDid())
                .maxWeightKg(caps.maxWeightKg())
                .maxDistanceKm(caps.maxDistanceKm())
                .worksWeekends(caps.worksWeekends())
                .worksNights(caps.worksNights())
                .acceptedPackageTypeCodes(caps.acceptedPackageTypeCodes())
                .specializationCodes(caps.specializationCodes())
                .createdAt(org.getCreatedAt())
                .updatedAt(org.getUpdatedAt())
                .version(org.getVersion())
                .build();
    }

    private FreelancerSubDelivererEntity toSubDelivererEntity(AssociatedDelivererRef ref,
                                                               UUID orgUuid) {
        return FreelancerSubDelivererEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .freelancerOrgId(orgUuid)
                .delivererActorId(ref.delivererActorId())
                .associationStatus(ref.status().name())
                .commissionRate(ref.commissionRate())
                .associatedSince(ref.associatedSince())
                .terminatedAt(ref.terminatedAt())
                .createdAt(Instant.now())
                .build();
    }

    private FreelancerOperationalZoneEntity toZoneEntity(ServiceZone zone, UUID orgUuid) {
        return FreelancerOperationalZoneEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .freelancerOrgId(orgUuid)
                .zoneName(zone.zoneName())
                .polygonWkt(zone.polygonBoundsWkt())
                .active(zone.active())
                .accessDifficulty(zone.accessDifficulty() != null
                        ? zone.accessDifficulty().name() : ZoneAccessDifficulty.LOW.name())
                .zoneType(zone.zoneType() != null
                        ? zone.zoneType().name() : DeliveryZoneType.URBAN.name())
                .build();
    }

    // ─── Mapping helpers — Entity → Domain ───────────────────────────────────

    private FreelancerOrganization toDomain(FreelancerOrgEntity entity,
                                             List<ServiceZone> zones,
                                             List<AssociatedDelivererRef> subDeliverers) {
        FreelancerCapabilities caps = new FreelancerCapabilities(
                entity.getMaxWeightKg(), entity.getMaxDistanceKm(),
                entity.isWorksWeekends(), entity.isWorksNights(),
                entity.getAcceptedPackageTypeCodes(), entity.getSpecializationCodes());

        FreelancerBillingProfile billing = new FreelancerBillingProfile(
                entity.getActivePolicyId(), entity.getDefaultTemplateCode(),
                entity.isVatApplicable(), entity.getTaxId());

        return new FreelancerOrganization(
                OrganizationId.of(entity.getId()),
                entity.getOrganizationId(),
                entity.getTenantId(),
                entity.getTradeName(),
                entity.getOwnerActorId(),
                FreelancerRegStatus.valueOf(entity.getRegistrationStatus()),
                KycLevel.valueOf(entity.getKycLevel()),
                billing,
                entity.getTrustScore(),
                entity.getBlockchainDid(),
                caps,
                zones,
                subDeliverers,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }

    private ServiceZone toServiceZone(FreelancerOperationalZoneEntity entity) {
        ZoneAccessDifficulty difficulty = entity.getAccessDifficulty() != null
                ? ZoneAccessDifficulty.valueOf(entity.getAccessDifficulty())
                : ZoneAccessDifficulty.LOW;
        DeliveryZoneType zoneType = entity.getZoneType() != null
                ? DeliveryZoneType.valueOf(entity.getZoneType())
                : DeliveryZoneType.URBAN;
        return new ServiceZone(
                entity.getZoneName(), entity.getPolygonWkt(),
                entity.isActive(), difficulty, zoneType);
    }

    private AssociatedDelivererRef toSubDelivererRef(FreelancerSubDelivererEntity entity,
                                                      OrganizationId orgId) {
        return new AssociatedDelivererRef(
                entity.getDelivererActorId(),
                orgId,
                AssociationStatus.valueOf(entity.getAssociationStatus()),
                entity.getCommissionRate(),
                entity.getAssociatedSince(),
                entity.getTerminatedAt());
    }
}
