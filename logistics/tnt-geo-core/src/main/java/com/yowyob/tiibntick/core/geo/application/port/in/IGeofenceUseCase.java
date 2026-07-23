package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — service zone geofencing checks.
 * Used by tnt-actor-core (deliverer zone eligibility) and
 * tnt-delivery-core (mission zone validation).
 *
 * Author: MANFOUO Braun
 */
public interface IGeofenceUseCase {

    /**
     * Checks whether a geographic point falls within a named service zone.
     *
     * <p>The zone lookup is scoped to {@code tenantId}: a zone belonging to another
     * tenant is treated as not found, never leaked across tenants.
     *
     * @param point    the coordinate to test
     * @param zoneId   the ID of the service zone polygon
     * @param tenantId the tenant scope the zone must belong to
     * @return Mono<Boolean> true if the point is inside the zone
     */
    Mono<Boolean> isPointInZone(GeoPoint point, UUID zoneId, UUID tenantId);

    /**
     * Checks whether a geographic point is within any zone belonging to an agency.
     *
     * @param point    the coordinate to test
     * @param agencyId the agency whose zones to check
     * @param tenantId the tenant scope
     * @return Mono<Boolean> true if covered by at least one active agency zone
     */
    Mono<Boolean> isPointCoveredByAgency(GeoPoint point, UUID agencyId, UUID tenantId);

    /**
     * Returns the ID of the zone containing the given point, if any.
     *
     * @param point    the coordinate to test
     * @param tenantId the tenant scope
     * @return Mono emitting the matching zone ID, or empty if not covered
     */
    Mono<UUID> findContainingZone(GeoPoint point, UUID tenantId);
}
