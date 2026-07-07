package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — spatial search for nearby relay hubs.
 * Used by tnt-delivery-core to find eligible deposit hubs for a mission.
 *
 * Author: MANFOUO Braun
 */
public interface IFindNearbyHubsUseCase {

    /**
     * Finds all active relay hubs within radiusKm of the given point, ordered by distance.
     *
     * @param center   the origin point
     * @param radiusKm the search radius
     * @param tenantId the tenant scope
     * @return a Flux of hubs ordered by proximity (closest first)
     */
    Flux<RelayHub> findNearbyAvailableHubs(GeoPoint center, double radiusKm, UUID tenantId);

    /**
     * Finds the nearest single available relay hub to the given point.
     */
    Mono<RelayHub> findNearestAvailableHub(GeoPoint center, UUID tenantId);

    /**
     * Returns hub details by ID.
     */
    Mono<RelayHub> findHub(UUID hubId, UUID tenantId);

    /**
     * Updates the occupancy of a relay hub (e.g. after parcel deposit/pickup).
     */
    Mono<RelayHub> updateHubOccupancy(UUID hubId, UUID tenantId, int newOccupancy);
}
