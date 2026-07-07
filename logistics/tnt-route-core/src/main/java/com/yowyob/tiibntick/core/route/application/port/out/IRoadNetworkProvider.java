package com.yowyob.tiibntick.core.route.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.RoadNetwork;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface IRoadNetworkProvider {
    Mono<RoadNetwork> loadNetwork(UUID tenantId);
}
