package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — persistence operations for RelayHub.
 *
 * Author: MANFOUO Braun
 */
public interface IRelayHubRepository {

    Mono<RelayHub> save(RelayHub hub);

    Mono<RelayHub> findById(UUID id, UUID tenantId);

    Flux<RelayHub> findByBranch(UUID branchId, UUID tenantId);

    Flux<RelayHub> findAllActive(UUID tenantId);

    /**
     * Spatial query: active hubs within radiusKm ordered by proximity to center.
     * Uses PostGIS ST_DWithin + ST_Distance for ordering.
     */
    Flux<RelayHub> findAvailableWithinRadius(UUID tenantId, GeoPoint center, double radiusKm);

    Mono<RelayHub> updateOccupancy(UUID id, UUID tenantId, int newOccupancy);
}
