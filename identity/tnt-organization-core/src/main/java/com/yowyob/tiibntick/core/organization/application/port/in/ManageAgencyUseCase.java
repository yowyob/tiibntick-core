package com.yowyob.tiibntick.core.organization.application.port.in;

import com.yowyob.tiibntick.core.organization.domain.model.Agency;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary inbound port — Agency management use cases.
 *
 * <p>Defines the contract exposed by the application layer for operations on the
 * {@link Agency} aggregate. Called by adapters (REST controllers, gRPC, Kafka consumers).
 *
 * <p>All operations are reactive (Reactor {@link Mono}/{@link Flux}).
 *
 * @author MANFOUO Braun
 */
public interface ManageAgencyUseCase {

    /**
     * Creates a new Agency and validates its Kernel organization reference beforehand.
     *
     * <p>Precondition: the {@code organizationId} must exist and be active in
     * RT-comops-organization-core (verified via {@code KernelOrganizationPort}).
     *
     * @param organizationId         Kernel organization UUID (must be active)
     * @param tenantId               Multi-tenant key
     * @param name                   Agency operating name
     * @param commerceRegistryNumber National registry number (nullable)
     * @param primaryCurrency        ISO 4217 code (null → defaults to XAF)
     * @return a {@link Mono} emitting the persisted {@link Agency}
     */
    Mono<Agency> createAgency(UUID organizationId,
                              UUID tenantId,
                              String name,
                              String commerceRegistryNumber,
                              String primaryCurrency);

    /**
     * Retrieves an Agency by its TiiBnTick internal ID.
     *
     * @param id the TiiBnTick agency ID
     * @return a {@link Mono} emitting the found Agency, or empty
     */
    Mono<Agency> findAgencyById(OrganizationId id);

    /**
     * Returns all Agencies linked to a given Kernel organization UUID.
     *
     * @param organizationId the Kernel organization UUID
     * @return a {@link Flux} of matching Agencies
     */
    Flux<Agency> findAgenciesByOrganizationId(UUID organizationId);

    /**
     * Returns all Agencies within a tenant.
     *
     * @param tenantId the multi-tenant key
     * @return a {@link Flux} of all Agencies in the tenant
     */
    Flux<Agency> listAgenciesForTenant(UUID tenantId);
}
