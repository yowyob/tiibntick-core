package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IManageRoadNetworkUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IGeoEventPublisher;
import com.yowyob.tiibntick.core.geo.application.port.out.IRoadArcRepository;
import com.yowyob.tiibntick.core.geo.application.port.out.IRoadNodeRepository;
import com.yowyob.tiibntick.core.geo.domain.event.RoadNodeCreatedEvent;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Application service for road network graph management.
 * Builds and maintains the directed weighted graph used by tnt-route-core.
 *
 * Author: MANFOUO Braun
 */
@Service
public class RoadNetworkService implements IManageRoadNetworkUseCase {

    private final IRoadNodeRepository nodeRepository;
    private final IRoadArcRepository arcRepository;
    private final IGeoEventPublisher eventPublisher;

    public RoadNetworkService(IRoadNodeRepository nodeRepository,
                              IRoadArcRepository arcRepository,
                              IGeoEventPublisher eventPublisher) {
        this.nodeRepository = nodeRepository;
        this.arcRepository = arcRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Mono<RoadNode> createNode(UUID tenantId, NodeType type, GeoPoint coordinates,
                                     String name, String cityCode, Integer capacitySlots) {
        RoadNode node = RoadNode.create(tenantId, type, coordinates, name, cityCode, capacitySlots);
        return nodeRepository.save(node)
                .flatMap(saved -> {
                    RoadNodeCreatedEvent event = RoadNodeCreatedEvent.of(
                            tenantId, saved.id().value(), saved.type().name(),
                            saved.coordinates().latitude(), saved.coordinates().longitude(),
                            saved.name(), saved.cityCode()
                    );
                    return eventPublisher.publishRoadNodeCreated(event)
                            .thenReturn(saved);
                });
    }

    @Override
    public Mono<RoadNode> findNode(String nodeId, UUID tenantId) {
        return nodeRepository.findById(RoadNodeId.of(nodeId), tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RoadNode", nodeId)));
    }

    @Override
    public Flux<RoadNode> findNodesByCity(UUID tenantId, String cityCode) {
        return nodeRepository.findByCityCode(tenantId, cityCode);
    }

    @Override
    public Flux<RoadNode> findNodesNearby(UUID tenantId, GeoPoint center, double radiusKm) {
        if (radiusKm <= 0) {
            return Flux.error(new IllegalArgumentException("radiusKm must be > 0"));
        }
        return nodeRepository.findWithinRadius(tenantId, center, radiusKm);
    }

    @Override
    public Mono<RoadNode> deactivateNode(String nodeId, UUID tenantId) {
        return nodeRepository.findById(RoadNodeId.of(nodeId), tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RoadNode", nodeId)))
                .flatMap(node -> {
                    node.deactivate();
                    return nodeRepository.save(node);
                });
    }

    @Override
    public Mono<RoadArc> createArc(UUID tenantId, String sourceNodeId, String targetNodeId,
                                    double distanceKm, RoadType roadType, double baseSpeedKmh,
                                    boolean bidirectional) {
        RoadNodeId sourceId = RoadNodeId.of(sourceNodeId);
        RoadNodeId targetId = RoadNodeId.of(targetNodeId);

        return Mono.zip(
                nodeRepository.existsById(sourceId, tenantId),
                nodeRepository.existsById(targetId, tenantId)
        ).flatMap(tuple -> {
            if (!tuple.getT1()) {
                return Mono.error(new GeoNotFoundException("RoadNode (source)", sourceNodeId));
            }
            if (!tuple.getT2()) {
                return Mono.error(new GeoNotFoundException("RoadNode (target)", targetNodeId));
            }
            RoadArc arc = RoadArc.createWithSpeed(tenantId, sourceId, targetId, distanceKm,
                    roadType, baseSpeedKmh, bidirectional);
            return arcRepository.save(arc);
        });
    }

    @Override
    public Mono<RoadArc> updateArcTrafficFactor(String arcId, UUID tenantId, double trafficFactor) {
        return arcRepository.updateTrafficFactor(RoadArcId.of(arcId), tenantId, trafficFactor)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RoadArc", arcId)));
    }

    @Override
    public Flux<RoadArc> findArcsBySource(String sourceNodeId, UUID tenantId) {
        return arcRepository.findBySourceId(RoadNodeId.of(sourceNodeId), tenantId);
    }

    /**
     * Loads the complete road network for a tenant into a RoadNetwork aggregate.
     * This is the primary call made by tnt-route-core's AStarPathfinder.
     * The result should be cached in Redis with TTL=5min by tnt-bootstrap.
     */
    @Override
    public Mono<RoadNetwork> loadNetwork(UUID tenantId) {
        Mono<List<RoadNode>> nodesMono = nodeRepository.findAllByTenant(tenantId)
                .filter(RoadNode::isActive)
                .collectList();
        Mono<List<RoadArc>> arcsMono = arcRepository.findAllByTenant(tenantId)
                .collectList();

        return Mono.zip(nodesMono, arcsMono)
                .map(tuple -> RoadNetwork.build(tenantId, tuple.getT1(), tuple.getT2()));
    }
}
