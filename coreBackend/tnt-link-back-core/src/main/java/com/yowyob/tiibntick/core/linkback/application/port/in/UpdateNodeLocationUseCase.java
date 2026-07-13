package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UpdateNodeLocationUseCase {

    /**
     * @param heading    optional compass bearing in degrees
     * @param polPeerCount peer count reported by the client for the Proof-of-Location
     *                     heuristic (see {@link NetworkNode} javadoc) — 0 if not supplied
     */
    Mono<NetworkNode> updateLocation(UUID tenantId, UUID nodeId, GeoPoint location, Double heading, int polPeerCount);
}
