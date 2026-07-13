package com.yowyob.tiibntick.core.agency.org.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port — synchronization with tnt-organization-core (Agency / Branch / HubRelais).
 * <p>
 * Registers the TNT logistics extension linked to an already provisioned
 * {@code kernelOrganizationId}.
 *
 * @see OrganizationCoreClient
 */
public interface OrganizationCorePort {

    Mono<UUID> registerAgency(RegisterAgencyRequest request);

    Mono<UUID> registerBranch(RegisterBranchRequest request);

    Mono<UUID> registerHub(RegisterHubRequest request);

    record RegisterAgencyRequest(
            UUID tenantId,
            UUID kernelOrganizationId,
            String name,
            String commerceRegistryNumber,
            String primaryCurrency
    ) {}

    record RegisterBranchRequest(
            UUID tenantId,
            UUID coreAgencyId,
            UUID kernelOrganizationId,
            String name,
            String address
    ) {}

    record RegisterHubRequest(
            UUID tenantId,
            UUID kernelOrganizationId,
            String name,
            int maxParcelCapacity,
            String geographicPointWkt,
            String openingHours
    ) {}
}
