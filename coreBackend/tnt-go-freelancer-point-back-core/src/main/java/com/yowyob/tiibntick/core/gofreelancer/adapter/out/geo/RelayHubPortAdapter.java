package com.yowyob.tiibntick.core.gofreelancer.adapter.out.geo;

import com.yowyob.tiibntick.core.geo.application.port.in.IFindNearbyHubsUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageRoadNetworkUseCase;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.NodeType;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Bridges GOFP to {@code tnt-geo-core} RelayHub (source of truth for hub identity / occupancy).
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayHubPortAdapter implements IRelayHubPort {

    private final IFindNearbyHubsUseCase findNearbyHubsUseCase;
    private final IManageRoadNetworkUseCase manageRoadNetworkUseCase;
    private final IManageRelayHubUseCase manageRelayHubUseCase;

    @Override
    public Mono<RelayHub> findHub(UUID hubId, UUID tenantId) {
        return findNearbyHubsUseCase.findHub(hubId, tenantId);
    }

    @Override
    public Mono<RelayHub> incrementOccupancy(UUID hubId, UUID tenantId) {
        return findNearbyHubsUseCase.findHub(hubId, tenantId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "RelayHub not found in geo-core: " + hubId)))
                .flatMap(hub -> {
                    if (!hub.isAvailable() || hub.availableSlots() <= 0) {
                        return Mono.error(new IllegalStateException(
                                "RelayHub " + hubId + " has no available slots (occupancy="
                                        + hub.currentOccupancy() + "/" + hub.capacitySlots() + ")"));
                    }
                    int next = hub.currentOccupancy() + 1;
                    log.info("Geo occupancy +1 hub={} → {}", hubId, next);
                    return findNearbyHubsUseCase.updateHubOccupancy(hubId, tenantId, next);
                });
    }

    @Override
    public Mono<RelayHub> decrementOccupancy(UUID hubId, UUID tenantId) {
        return findNearbyHubsUseCase.findHub(hubId, tenantId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "RelayHub not found in geo-core: " + hubId)))
                .flatMap(hub -> {
                    if (hub.currentOccupancy() <= 0) {
                        log.warn("Geo occupancy already 0 for hub {} — skip decrement", hubId);
                        return Mono.just(hub);
                    }
                    int next = hub.currentOccupancy() - 1;
                    log.info("Geo occupancy -1 hub={} → {}", hubId, next);
                    return findNearbyHubsUseCase.updateHubOccupancy(hubId, tenantId, next);
                });
    }

    @Override
    public Mono<RelayHub> provisionHub(UUID tenantId, GeoPoint coordinates, String name, String cityCode,
                                        int capacitySlots, String operatorActorId) {
        return manageRoadNetworkUseCase
                .createNode(tenantId, NodeType.RELAY_HUB, coordinates, name, cityCode, capacitySlots)
                .doOnSuccess(node -> log.info("Provisioned RoadNode {} for relay hub '{}'", node.id(), name))
                .flatMap(node -> manageRelayHubUseCase.createHub(
                        tenantId, null, node.id().value(), capacitySlots, operatorActorId))
                .doOnSuccess(hub -> log.info("Provisioned RelayHub {} (freelance, no branch) for '{}'",
                        hub.id(), name));
    }
}
