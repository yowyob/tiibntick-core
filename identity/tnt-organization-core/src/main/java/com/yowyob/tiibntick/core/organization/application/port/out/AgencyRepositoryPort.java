package com.yowyob.tiibntick.core.organization.application.port.out;

import com.yowyob.tiibntick.core.organization.domain.model.Agency;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound persistence port for the {@link Agency} aggregate.
 *
 * <p>Defines the reactive contract for persisting and querying Agency aggregates.
 * The adapter implementation uses Spring Data R2DBC with PostgreSQL.
 *
 * <p>This is a secondary port (driven port) in the hexagonal architecture.
 *
 * @author MANFOUO Braun
 */
public interface AgencyRepositoryPort {

    /**
     * Persists a new Agency or updates an existing one.
     *
     * @param agency the Agency aggregate to save
     * @return a {@link Mono} emitting the saved Agency (with any generated fields populated)
     */
    Mono<Agency> save(Agency agency);

    /**
     * Finds an Agency by its TiiBnTick internal ID.
     *
     * @param id the TiiBnTick internal agency ID
     * @return a {@link Mono} emitting the found Agency, or empty if not found
     */
    Mono<Agency> findById(OrganizationId id);

    /**
     * Finds an Agency by its Kernel organization UUID.
     *
     * <p>Since the {@code organizationId} is a logical integration key, multiple
     * TiiBnTick agencies may theoretically map to the same Kernel org
     * (e.g., in multi-tenant scenarios). This method returns all matches.
     *
     * @param organizationId the Kernel organization UUID
     * @return a {@link Flux} of matching Agencies
     */
    Flux<Agency> findByOrganizationId(UUID organizationId);

    /**
     * Returns all Agencies within a given tenant.
     *
     * @param tenantId the multi-tenant key
     * @return a {@link Flux} of all Agencies for the tenant
     */
    Flux<Agency> findAllByTenantId(UUID tenantId);

    /**
     * Checks whether an Agency with the given internal ID exists.
     *
     * @param id the TiiBnTick internal agency ID
     * @return a {@link Mono} emitting {@code true} if found, {@code false} otherwise
     */
    Mono<Boolean> existsById(OrganizationId id);
}
