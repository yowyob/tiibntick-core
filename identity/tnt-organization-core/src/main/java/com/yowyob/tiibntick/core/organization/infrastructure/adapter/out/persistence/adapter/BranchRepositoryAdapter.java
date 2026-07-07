package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.organization.application.port.out.BranchRepositoryPort;
import com.yowyob.tiibntick.core.organization.domain.enums.DeliveryZoneType;
import com.yowyob.tiibntick.core.organization.domain.enums.ZoneAccessDifficulty;
import com.yowyob.tiibntick.core.organization.domain.model.Branch;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.BranchEntity;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository.BranchR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence adapter implementing {@link BranchRepositoryPort} using Spring Data R2DBC.
 *
 * <p>Handles the bidirectional mapping between {@link Branch} domain aggregates and
 * {@link BranchEntity} R2DBC entities. The {@link ServiceZone} value object is
 * denormalized into four columns ({@code service_zone_name}, {@code service_zone_wkt},
 * {@code service_zone_access_difficulty}, {@code service_zone_type}).
 *
 * <h3> changes</h3>
 * <ul>
 *   <li>ServiceZone now has {@code accessDifficulty} and {@code zoneType} — mapped
 *       to/from {@link BranchEntity} fields {@code serviceZoneAccessDifficulty}
 *       and {@code serviceZoneType}. Defaults applied if columns are null (backward
 *       compatibility with rows created before ).</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
public class BranchRepositoryAdapter implements BranchRepositoryPort {

    private final BranchR2dbcRepository repository;

    /**
     * Constructor injection.
     *
     * @param repository the Spring Data R2DBC repository
     */
    public BranchRepositoryAdapter(BranchR2dbcRepository repository) {
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Branch> save(Branch branch) {
        return repository.existsById(branch.getId().value())
                .flatMap(exists -> {
                    var entity = toEntity(branch);
                    entity.setNew(!exists);
                    return repository.save(entity);
                })
                .map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Branch> findById(OrganizationId id) {
        return repository.findById(id.value()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<Branch> findByAgencyId(OrganizationId agencyId) {
        return repository.findByAgencyId(agencyId.value()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<Branch> findByOrganizationId(UUID organizationId) {
        return repository.findByOrganizationId(organizationId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<Branch> findActiveByTenantId(UUID tenantId) {
        return repository.findByTenantIdAndActive(tenantId, true).map(this::toDomain);
    }

    // ─── Mapping helpers ─────────────────────────────────────────────────────

    /**
     * Maps a {@link Branch} domain aggregate to a {@link BranchEntity} for persistence.
     * The {@link ServiceZone} VO is denormalized into four separate columns.
     *
     * @param branch the domain aggregate
     * @return the corresponding R2DBC entity
     */
    private BranchEntity toEntity(Branch branch) {
        ServiceZone zone = branch.getServiceZone();
        return BranchEntity.builder()
                .id(branch.getId().value())
                .organizationId(branch.getOrganizationId())
                .agencyId(branch.getAgencyId().value())
                .tenantId(branch.getTenantId())
                .name(branch.getName())
                .address(branch.getAddress())
                .serviceZoneName(zone != null ? zone.zoneName() : null)
                .serviceZoneWkt(zone != null ? zone.polygonBoundsWkt() : null)
                .active(branch.isActive())
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }

    /**
     * Maps a {@link BranchEntity} to a {@link Branch} domain aggregate.
     * Reconstructs the {@link ServiceZone} VO including the  classification fields.
     * Applies safe defaults when columns are null (backward compatibility).
     *
     * @param entity the R2DBC entity from the database
     * @return the reconstituted domain aggregate
     */
    private Branch toDomain(BranchEntity entity) {
        ServiceZone zone = null;
        if (entity.getServiceZoneName() != null && entity.getServiceZoneWkt() != null) {
            // Safe defaults for rows created before  migration
            ZoneAccessDifficulty difficulty = ZoneAccessDifficulty.LOW;
            DeliveryZoneType zoneType = DeliveryZoneType.URBAN;
            zone = new ServiceZone(
                    entity.getServiceZoneName(),
                    entity.getServiceZoneWkt(),
                    entity.isActive(),
                    difficulty,
                    zoneType);
        }
        return new Branch(
                OrganizationId.of(entity.getId()),
                entity.getOrganizationId(),
                OrganizationId.of(entity.getAgencyId()),
                entity.getTenantId(),
                entity.getName(),
                entity.getAddress(),
                zone,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
