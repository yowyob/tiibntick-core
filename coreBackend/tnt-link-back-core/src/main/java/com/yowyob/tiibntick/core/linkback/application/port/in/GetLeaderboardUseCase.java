package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface GetLeaderboardUseCase {

    /** Nodes ranked by trust score then gamification points, descending. */
    Flux<NetworkNode> getTopNodes(UUID tenantId, int limit);
}
