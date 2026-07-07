package com.yowyob.tiibntick.core.route.application.port.in;

import com.yowyob.tiibntick.core.route.domain.model.OptimizedRoute;
import com.yowyob.tiibntick.core.route.domain.model.Tour;
import com.yowyob.tiibntick.core.route.domain.model.VrpRequest;
import com.yowyob.tiibntick.core.route.domain.model.VrpSolution;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IOptimizeTourUseCase {
    Mono<VrpSolution> solveVrp(VrpRequest request);
    Mono<OptimizedRoute> optimizeSingleRoute(String originNodeId, String destinationNodeId,
                                              UUID tenantId);
    Mono<List<Tour>> planDayTours(UUID tenantId, UUID agencyId, LocalDate date,
                                  List<String> delivererIds);
}
