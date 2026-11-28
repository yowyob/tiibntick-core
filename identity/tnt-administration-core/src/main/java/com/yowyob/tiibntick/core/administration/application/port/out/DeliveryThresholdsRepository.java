package com.yowyob.tiibntick.core.administration.application.port.out;

import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Persistence of the platform thresholds and of the organization overrides. */
public interface DeliveryThresholdsRepository {

    /** The platform row (seeded by changeset {@code evt-l1-001}); empty if missing. */
    Mono<PlatformDeliveryThresholds> findPlatform();

    /**
     * Replaces the platform row when its version is still {@code expectedVersion}; the stored version is incremented.
     *
     * @return the stored row, or empty when the version is stale
     */
    Mono<PlatformDeliveryThresholds> updatePlatform(PlatformDeliveryThresholds next, long expectedVersion);

    Mono<OrganizationDeliveryThresholds> findOrganization(UUID organizationId);

    Flux<OrganizationDeliveryThresholds> findAllOrganizations();

    /** Inserts a new override (version 0); empty when one already exists. */
    Mono<OrganizationDeliveryThresholds> insertOrganization(OrganizationDeliveryThresholds override);

    /** Replaces an override when its version is still {@code expectedVersion}; empty when stale or missing. */
    Mono<OrganizationDeliveryThresholds> updateOrganization(OrganizationDeliveryThresholds override,
                                                            long expectedVersion);

    /** @return whether a row was deleted */
    Mono<Boolean> deleteOrganization(UUID organizationId);
}
