package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — CRUD operations on the road network graph (nodes and arcs).
 *
 * Author: MANFOUO Braun
 */
public interface IManageRoadNetworkUseCase {

    Mono<RoadNode> createNode(UUID tenantId, NodeType type, GeoPoint coordinates,
                              String name, String cityCode, Integer capacitySlots);

    Mono<RoadNode> findNode(String nodeId, UUID tenantId);

    Flux<RoadNode> findNodesByCity(UUID tenantId, String cityCode);

    Flux<RoadNode> findNodesNearby(UUID tenantId, GeoPoint center, double radiusKm);

    Mono<RoadNode> deactivateNode(String nodeId, UUID tenantId);

    Mono<RoadArc> createArc(UUID tenantId, String sourceNodeId, String targetNodeId,
                             double distanceKm, RoadType roadType, double baseSpeedKmh,
                             boolean bidirectional);

    Mono<RoadArc> updateArcTrafficFactor(String arcId, UUID tenantId, double trafficFactor);

    Flux<RoadArc> findArcsBySource(String sourceNodeId, UUID tenantId);

    Mono<RoadNetwork> loadNetwork(UUID tenantId);
}
