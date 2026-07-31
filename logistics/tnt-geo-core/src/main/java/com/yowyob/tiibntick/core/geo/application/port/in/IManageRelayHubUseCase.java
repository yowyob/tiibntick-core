package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — provisioning and branch-scoped lookup of relay hubs.
 *
 * <p>Complements {@link IFindNearbyHubsUseCase} (spatial search / occupancy updates)
 * with the operations needed to actually create a hub in the first place. Callers from
 * other product backends (e.g. GOFP relay points) pass {@code branchId == null} to
 * provision a freelance/independent hub with no agency branch.
 *
 * @author MANFOUO Braun
 */
public interface IManageRelayHubUseCase {

    /**
     * Creates a new relay hub on an existing road node.
     *
     * @param branchId agency branch owning the hub, or {@code null} for a
     *                 freelance/independent hub
     * @param nodeId   id of a pre-existing {@code RoadNode} (see {@link IManageRoadNetworkUseCase})
     */
    Mono<RelayHub> createHub(UUID tenantId, UUID branchId, String nodeId,
                             int capacitySlots, String operatorActorId);

    Flux<RelayHub> findHubsByBranch(UUID branchId, UUID tenantId);
}
