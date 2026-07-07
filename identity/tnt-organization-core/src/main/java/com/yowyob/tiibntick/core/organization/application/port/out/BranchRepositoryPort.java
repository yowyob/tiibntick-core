package com.yowyob.tiibntick.core.organization.application.port.out;

import com.yowyob.tiibntick.core.organization.domain.model.Branch;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound persistence port for the {@link Branch} aggregate.
 *
 * <p>Defines the reactive contract for persisting and querying Branch aggregates.
 * The adapter implementation uses Spring Data R2DBC with PostgreSQL.
 *
 * <p>This is a secondary port (driven port) in the hexagonal architecture.
 *
 * @author MANFOUO Braun
 */
public interface BranchRepositoryPort {

    /**
     * Persists a new Branch or updates an existing one.
     *
     * @param branch the Branch aggregate to save
     * @return a {@link Mono} emitting the saved Branch
     */
    Mono<Branch> save(Branch branch);

    /**
     * Finds a Branch by its TiiBnTick internal ID.
     *
     * @param id the TiiBnTick internal branch ID
     * @return a {@link Mono} emitting the found Branch, or empty if not found
     */
    Mono<Branch> findById(OrganizationId id);

    /**
     * Returns all Branches belonging to a given Agency.
     *
     * @param agencyId the parent Agency's TiiBnTick internal ID
     * @return a {@link Flux} of Branches for the given agency
     */
    Flux<Branch> findByAgencyId(OrganizationId agencyId);

    /**
     * Returns all Branches referencing a given Kernel organization UUID.
     *
     * @param organizationId the Kernel organization UUID
     * @return a {@link Flux} of matching Branches
     */
    Flux<Branch> findByOrganizationId(UUID organizationId);

    /**
     * Returns all active Branches for a given tenant.
     *
     * @param tenantId the multi-tenant key
     * @return a {@link Flux} of active Branches
     */
    Flux<Branch> findActiveByTenantId(UUID tenantId);
}
