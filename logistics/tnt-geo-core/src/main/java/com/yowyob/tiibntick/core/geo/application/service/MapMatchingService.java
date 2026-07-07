package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.out.IRoadNodeRepository;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Service that snaps raw GPS coordinates to the nearest node in the road network.
 * Used by tnt-realtime-core to convert incoming GPS pings from deliverers
 * into routable graph positions for A* traversal.
 *
 * Strategy: within a configurable snap radius, find the nearest active node.
 * Falls back to the raw GPS point if no node is found within the radius.
 *
 * Author: MANFOUO Braun
 */
@Service
public class MapMatchingService {

    private static final double DEFAULT_SNAP_RADIUS_KM = 0.3;

    private final IRoadNodeRepository nodeRepository;

    public MapMatchingService(IRoadNodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /**
     * Snaps a GPS coordinate to the nearest road network node.
     *
     * @param rawGps   the raw GPS ping coordinates
     * @param tenantId the tenant scope
     * @return the nearest road node, or empty if none found within the snap radius
     */
    public Mono<RoadNode> snapToNearestNode(GeoPoint rawGps, UUID tenantId) {
        return snapToNearestNode(rawGps, tenantId, DEFAULT_SNAP_RADIUS_KM);
    }

    /**
     * Snaps a GPS coordinate to the nearest road network node within a given radius.
     */
    public Mono<RoadNode> snapToNearestNode(GeoPoint rawGps, UUID tenantId, double radiusKm) {
        return nodeRepository.findWithinRadius(tenantId, rawGps, radiusKm)
                .collectList()
                .flatMap(nodes -> {
                    if (nodes.isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.just(findNearest(nodes, rawGps));
                });
    }

    /**
     * Matches a sequence of GPS pings to an ordered sequence of network nodes.
     * Useful for reconstructing a deliverer's traveled path.
     *
     * @param gpsPings ordered list of raw GPS coordinates (chronological)
     * @param tenantId the tenant scope
     * @return a Mono emitting the list of matched nodes in the same order
     */
    public Mono<List<RoadNode>> matchTrace(List<GeoPoint> gpsPings, UUID tenantId) {
        return reactor.core.publisher.Flux.fromIterable(gpsPings)
                .flatMapSequential(ping ->
                        snapToNearestNode(ping, tenantId)
                                .onErrorResume(ex -> Mono.empty())
                )
                .distinct(node -> node.id().value())
                .collectList();
    }

    private RoadNode findNearest(List<RoadNode> candidates, GeoPoint target) {
        RoadNode nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (RoadNode node : candidates) {
            double dist = node.coordinates().haversineDistanceTo(target);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = node;
            }
        }
        return nearest;
    }
}
