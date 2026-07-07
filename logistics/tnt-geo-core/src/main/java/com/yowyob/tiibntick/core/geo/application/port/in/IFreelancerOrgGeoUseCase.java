package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.DeliveryZoneType;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.ServiceZonePolygon;
import com.yowyob.tiibntick.core.geo.domain.model.ZoneAccessDifficulty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port: geographic operations for FreelancerOrganizations.
 *
 * <p>Supports the FreelancerOrg model () by exposing:
 * <ol>
 *   <li>Zone access difficulty computation — used as DSL variable {@code zoneAccessDifficulty}.</li>
 *   <li>Delivery zone type resolution — used as DSL variable {@code deliveryZoneType}.</li>
 *   <li>FreelancerOrg zone discovery — find which orgs cover a given coordinate.</li>
 *   <li>FreelancerOrg-specific zone checking — validate an org's service coverage.</li>
 * </ol>
 *
 * <p>Implemented by {@code FreelancerOrgGeoService}.
 *
 * @author MANFOUO Braun
 */
public interface IFreelancerOrgGeoUseCase {

    /**
     * Computes the access difficulty for a delivery destination.
     *
     * <p>Used by tnt-billing-pricing to populate the {@code zoneAccessDifficulty}
     * DSL context variable, which can trigger surcharges for difficult zones.
     *
     * <p>Difficulty is derived from: road type of nearest arc,
     * geo elevation data, known flood-prone zones, and traffic congestion signals.
     *
     * @param lat latitude of the destination
     * @param lng longitude of the destination
     * @return computed zone access difficulty
     */
    Mono<ZoneAccessDifficulty> computeZoneAccessDifficulty(double lat, double lng);

    /**
     * Resolves the delivery zone type for a given coordinate.
     *
     * <p>Used by tnt-billing-pricing to populate the {@code deliveryZoneType}
     * DSL context variable, which selects the appropriate pricing zone bracket.
     *
     * @param lat latitude of the delivery destination
     * @param lng longitude of the delivery destination
     * @return resolved delivery zone type
     */
    Mono<DeliveryZoneType> resolveDeliveryZoneType(double lat, double lng);

    /**
     * Returns all FreelancerOrg IDs whose service zone covers the given coordinate.
     *
     * <p>Used by the announcement marketplace (TiiBnPick) to identify eligible
     * FreelancerOrgs for a client's delivery request.
     *
     * @param lat      latitude
     * @param lng      longitude
     * @param radiusKm search radius in km (additional radius beyond polygon boundary)
     * @param tenantId tenant scope
     * @return stream of FreelancerOrg UUIDs whose zone covers this point
     */
    Flux<String> findFreelancerOrgsInZone(double lat, double lng, double radiusKm, UUID tenantId);

    /**
     * Checks whether a specific FreelancerOrg's service zone covers the given coordinate.
     *
     * <p>Used by tnt-delivery-core to validate that the selected FreelancerOrg can
     * execute a delivery at the destination.
     *
     * @param freelancerOrgId the FreelancerOrg UUID (from tnt-organization-core)
     * @param lat             latitude
     * @param lng             longitude
     * @param tenantId        tenant scope
     * @return true if the org's zone covers this coordinate
     */
    Mono<Boolean> isInFreelancerOrgZone(String freelancerOrgId, double lat, double lng, UUID tenantId);

    /**
     * Creates or updates a service zone for a FreelancerOrg.
     *
     * <p>Called when a FreelancerOrg owner defines their delivery coverage area.
     *
     * @param tenantId        tenant scope
     * @param freelancerOrgId the FreelancerOrg UUID
     * @param name            zone display name
     * @param vertices        polygon vertices (minimum 3 points)
     * @return the created/saved ServiceZonePolygon
     */
    Mono<ServiceZonePolygon> defineFreelancerOrgZone(UUID tenantId, String freelancerOrgId,
                                                      String name, java.util.List<GeoPoint> vertices);
}
