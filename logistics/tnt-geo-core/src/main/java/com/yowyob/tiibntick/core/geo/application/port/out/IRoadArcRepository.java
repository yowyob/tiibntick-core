package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.RoadArc;
import com.yowyob.tiibntick.core.geo.domain.model.RoadArcId;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — persistence operations for RoadArc.
 *
 * Author: MANFOUO Braun
 */
public interface IRoadArcRepository {

    Mono<RoadArc> save(RoadArc arc);

    Mono<RoadArc> findById(RoadArcId id, UUID tenantId);

    Flux<RoadArc> findAllByTenant(UUID tenantId);

    Flux<RoadArc> findBySourceId(RoadNodeId sourceId, UUID tenantId);

    Flux<RoadArc> findByTargetId(RoadNodeId targetId, UUID tenantId);

    Mono<RoadArc> updateTrafficFactor(RoadArcId id, UUID tenantId, double trafficFactor);

    Mono<Void> deleteById(RoadArcId id, UUID tenantId);
    /**
     * Finds road arcs within the given radius of the coordinate.
     * Used by FreelancerOrgGeoService to infer zone access difficulty
     * and delivery zone type.
     *
     * @param lat         latitude of the query point
     * @param lng         longitude of the query point
     * @param radiusKm    search radius in km
     * @return Flux of nearby road arcs, sorted by distance ascending
     */
    default reactor.core.publisher.Flux<RoadArc> findNearestToCoordinate(double lat, double lng, double radiusKm) {
        // Default: return empty — concrete adapter overrides with PostGIS ST_DWithin query
        return reactor.core.publisher.Flux.empty();
    }

}