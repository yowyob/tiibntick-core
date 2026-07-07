package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.BranchEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC reactive repository for {@link BranchEntity}.
 *
 * <p>Provides basic CRUD operations plus custom derived queries for finding Branches
 * by parent agency, by Kernel integration key, and by tenant.
 *
 * @author MANFOUO Braun
 */
public interface BranchR2dbcRepository extends ReactiveCrudRepository<BranchEntity, UUID> {

    /**
     * Finds all Branch entities belonging to a given parent Agency.
     *
     * @param agencyId the parent Agency's TiiBnTick UUID
     * @return a reactive stream of matching Branch entities
     */
    Flux<BranchEntity> findByAgencyId(UUID agencyId);

    /**
     * Finds all Branch entities whose {@code organization_id} matches the given Kernel UUID.
     *
     * @param organizationId the Kernel organization UUID (integration key)
     * @return a reactive stream of matching Branch entities
     */
    Flux<BranchEntity> findByOrganizationId(UUID organizationId);

    /**
     * Finds all active Branch entities for a given tenant.
     *
     * @param tenantId the multi-tenant key
     * @param active   the operational status filter
     * @return a reactive stream of matching Branch entities
     */
    Flux<BranchEntity> findByTenantIdAndActive(UUID tenantId, boolean active);
}
