package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.AgencyEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC reactive repository for {@link AgencyEntity}.
 *
 * <p>Provides basic CRUD operations plus custom derived queries for finding Agencies
 * by their Kernel integration key ({@code organizationId}) and by tenant.
 *
 * @author MANFOUO Braun
 */
public interface AgencyR2dbcRepository extends ReactiveCrudRepository<AgencyEntity, UUID> {

    /**
     * Finds all Agency entities whose {@code organization_id} matches the given Kernel UUID.
     *
     * @param organizationId the Kernel organization UUID (integration key)
     * @return a reactive stream of matching Agency entities
     */
    Flux<AgencyEntity> findByOrganizationId(UUID organizationId);

    /**
     * Finds all Agency entities for a given tenant.
     *
     * @param tenantId the multi-tenant key
     * @return a reactive stream of all Agency entities in the tenant
     */
    Flux<AgencyEntity> findByTenantId(UUID tenantId);
}
