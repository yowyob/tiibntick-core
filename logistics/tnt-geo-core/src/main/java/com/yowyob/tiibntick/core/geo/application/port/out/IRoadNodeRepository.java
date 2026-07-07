package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNode;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — persistence operations for RoadNode aggregate.
 *
 * Author: MANFOUO Braun
 */
public interface IRoadNodeRepository {

    Mono<RoadNode> save(RoadNode node);

    Mono<RoadNode> findById(RoadNodeId id, UUID tenantId);

    Flux<RoadNode> findAllByTenant(UUID tenantId);

    Flux<RoadNode> findByCityCode(UUID tenantId, String cityCode);

    /**
     * Spatial query: finds all active nodes within radiusKm of the center.
     * Uses PostGIS ST_DWithin for sub-millisecond performance on GiST-indexed data.
     */
    Flux<RoadNode> findWithinRadius(UUID tenantId, GeoPoint center, double radiusKm);

    Mono<Boolean> existsById(RoadNodeId id, UUID tenantId);

    Mono<Void> deleteById(RoadNodeId id, UUID tenantId);
}
