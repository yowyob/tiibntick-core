package com.yowyob.tiibntick.core.route.application.service;

import com.yowyob.tiibntick.core.route.application.port.in.IOptimizeTourUseCase;
import com.yowyob.tiibntick.core.route.application.port.out.IRoadNetworkProvider;
import com.yowyob.tiibntick.core.route.application.port.out.IRouteEventPublisher;
import com.yowyob.tiibntick.core.route.application.port.out.ITourRepository;
import com.yowyob.tiibntick.core.route.domain.model.*;
import com.yowyob.tiibntick.core.geo.domain.model.CostWeights;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class TourPlannerService implements IOptimizeTourUseCase {

    private final VrpSolverService vrpSolverService;
    private final AStarPathfinderService pathfinderService;
    private final EtaComputeService etaComputeService;
    private final ITourRepository tourRepository;
    private final IRouteEventPublisher eventPublisher;
    private final IRoadNetworkProvider networkProvider;

    public TourPlannerService(VrpSolverService vrpSolverService,
                              AStarPathfinderService pathfinderService,
                              EtaComputeService etaComputeService,
                              ITourRepository tourRepository,
                              IRouteEventPublisher eventPublisher,
                              IRoadNetworkProvider networkProvider) {
        this.vrpSolverService = vrpSolverService;
        this.pathfinderService = pathfinderService;
        this.etaComputeService = etaComputeService;
        this.tourRepository = tourRepository;
        this.eventPublisher = eventPublisher;
        this.networkProvider = networkProvider;
    }

    @Override
    public Mono<VrpSolution> solveVrp(VrpRequest request) {
        return vrpSolverService.solve(request);
    }

    @Override
    public Mono<OptimizedRoute> optimizeSingleRoute(String originNodeId, String destinationNodeId,
                                                     UUID tenantId) {
        CostWeights weights = CostWeights.defaultWeights();
        return pathfinderService.findShortestPath(originNodeId, destinationNodeId, weights, tenantId)
                .map(path -> {
                    EtaResult eta = etaComputeService.computeInitial(path, Instant.now());
                    List<RouteWaypoint> waypoints = new ArrayList<>();
                    int seq = 0;
                    for (var nodeId : path.nodeSequence()) {
                        WaypointType type = (seq == 0) ? WaypointType.ORIGIN_DEPOT
                                : (seq == path.nodeCount() - 1) ? WaypointType.DELIVERY_POINT
                                : WaypointType.INTERMEDIATE_STOP;
                        waypoints.add(new RouteWaypoint(seq++, nodeId.value(), type,
                                com.yowyob.tiibntick.core.geo.domain.model.GeoPoint.of(0, 0),
                                null, null, null, 0));
                    }
                    int durationMin = (int) eta.remainingMinutes(Instant.now());
                    return OptimizedRoute.create(waypoints, path.totalDistanceKm(),
                            durationMin, path.totalCompositeCost(), eta, Map.of(), "A*");
                });
    }

    @Override
    public Mono<List<Tour>> planDayTours(UUID tenantId, UUID agencyId, LocalDate date,
                                          List<String> delivererIds) {
        return Mono.just(new ArrayList<>());
    }
}
