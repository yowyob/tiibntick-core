package com.yowyob.tiibntick.core.route.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.CostWeights;
import com.yowyob.tiibntick.core.route.domain.model.RoutePath;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface IComputeShortestPathUseCase {
    Mono<RoutePath> findShortestPath(String originNodeId, String destinationNodeId,
                                      CostWeights weights, UUID tenantId);
}
