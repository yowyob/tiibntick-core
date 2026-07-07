package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IFindNearbyHubsUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRepository;
import com.yowyob.tiibntick.core.geo.application.port.out.IRoadNodeRepository;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for relay hub management and spatial proximity searches.
 *
 * Author: MANFOUO Braun
 */
@Service
public class RelayHubService implements IFindNearbyHubsUseCase {

    private static final double DEFAULT_SEARCH_RADIUS_KM = 10.0;

    private final IRelayHubRepository hubRepository;
    private final IRoadNodeRepository nodeRepository;

    public RelayHubService(IRelayHubRepository hubRepository,
                           IRoadNodeRepository nodeRepository) {
        this.hubRepository = hubRepository;
        this.nodeRepository = nodeRepository;
    }

    @Override
    public Flux<RelayHub> findNearbyAvailableHubs(GeoPoint center, double radiusKm, UUID tenantId) {
        double radius = radiusKm > 0 ? radiusKm : DEFAULT_SEARCH_RADIUS_KM;
        return hubRepository.findAvailableWithinRadius(tenantId, center, radius);
    }

    @Override
    public Mono<RelayHub> findNearestAvailableHub(GeoPoint center, UUID tenantId) {
        return hubRepository.findAvailableWithinRadius(tenantId, center, DEFAULT_SEARCH_RADIUS_KM)
                .next()
                .switchIfEmpty(
                        hubRepository.findAvailableWithinRadius(tenantId, center, DEFAULT_SEARCH_RADIUS_KM * 5)
                                .next()
                );
    }

    @Override
    public Mono<RelayHub> findHub(UUID hubId, UUID tenantId) {
        return hubRepository.findById(hubId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RelayHub", hubId.toString())));
    }

    @Override
    public Mono<RelayHub> updateHubOccupancy(UUID hubId, UUID tenantId, int newOccupancy) {
        return hubRepository.findById(hubId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RelayHub", hubId.toString())))
                .flatMap(hub -> {
                    hub.updateOccupancy(newOccupancy);
                    return hubRepository.save(hub);
                });
    }

    public Mono<RelayHub> createHub(UUID tenantId, UUID branchId, String nodeId,
                                     int capacitySlots, String operatorActorId) {
        RoadNodeId roadNodeId = RoadNodeId.of(nodeId);
        return nodeRepository.existsById(roadNodeId, tenantId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new GeoNotFoundException("RoadNode", nodeId));
                    }
                    RelayHub hub = RelayHub.create(tenantId, branchId, roadNodeId, capacitySlots, operatorActorId);
                    return hubRepository.save(hub);
                });
    }

    public Flux<RelayHub> findHubsByBranch(UUID branchId, UUID tenantId) {
        return hubRepository.findByBranch(branchId, tenantId);
    }

    public Mono<RelayHub> addParcelToHub(UUID hubId, UUID tenantId) {
        return hubRepository.findById(hubId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RelayHub", hubId.toString())))
                .flatMap(hub -> {
                    hub.addParcel();
                    return hubRepository.save(hub);
                });
    }

    public Mono<RelayHub> removeParcelFromHub(UUID hubId, UUID tenantId) {
        return hubRepository.findById(hubId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RelayHub", hubId.toString())))
                .flatMap(hub -> {
                    hub.removeParcel();
                    return hubRepository.save(hub);
                });
    }
}
