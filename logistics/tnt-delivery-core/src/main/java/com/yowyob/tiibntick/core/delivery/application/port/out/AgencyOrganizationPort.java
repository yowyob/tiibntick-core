package com.yowyob.tiibntick.core.delivery.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Converts {@code Delivery.agencyId} — an id of the agency registry ({@code agency_org.agencies.id}) — into the Kernel
 * organisation id under which the threshold overrides are stored ({@code kernel_organization_id}).
 *
 * <p>Implemented by the agency back-core (a higher layer: it owns the registry). Without an implementation the
 * platform thresholds apply.
 */
public interface AgencyOrganizationPort {

    /** @return the Kernel organisation id of the agency, empty when unknown */
    Mono<UUID> kernelOrganizationId(UUID agencyRegistryId);
}
