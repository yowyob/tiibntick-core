package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface QueryNetworkNodesUseCase {

    Mono<NetworkNode> findById(UUID tenantId, UUID nodeId);

    /** The caller's own registered node, if any (looked up by the actor/org id it extends). */
    Mono<NetworkNode> findByRefId(UUID tenantId, UUID refId);

    /** Nodes located within the given bounding box, mirroring the Link frontend's getNearby(bbox) call. */
    Flux<NetworkNode> findWithinBoundingBox(UUID tenantId, double minLat, double minLng, double maxLat, double maxLng);
}
