package com.yowyob.tiibntick.core.route.application.service;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNetwork;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNode;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import com.yowyob.tiibntick.core.route.application.port.out.IRoadNetworkProvider;
import com.yowyob.tiibntick.core.route.application.port.out.IRouteEventPublisher;
import com.yowyob.tiibntick.core.route.domain.event.VrpFallbackActivatedEvent;
import com.yowyob.tiibntick.core.route.domain.model.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@Service
public class VrpSolverService {

    static {
        Loader.loadNativeLibraries();
    }

    private final IRoadNetworkProvider networkProvider;
    private final AStarPathfinderService pathfinder;
    private final IRouteEventPublisher eventPublisher;

    public VrpSolverService(IRoadNetworkProvider networkProvider,
                            AStarPathfinderService pathfinder,
                            IRouteEventPublisher eventPublisher) {
        this.networkProvider = networkProvider;
        this.pathfinder = pathfinder;
        this.eventPublisher = eventPublisher;
    }

    public Mono<VrpSolution> solve(VrpRequest request) {
        return networkProvider.loadNetwork(UUID.fromString(request.tenantId()))
                .flatMap(network -> Mono.fromCallable(() -> solveBlocking(request, network))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private VrpSolution solveBlocking(VrpRequest request, RoadNetwork network) {
        long startMs = System.currentTimeMillis();
        List<DeliveryItem> deliveries = request.deliveries();
        String depotId = request.depotNodeId();

        List<String> allNodeIds = new ArrayList<>();
        allNodeIds.add(depotId);
        for (DeliveryItem d : deliveries) {
            if (!allNodeIds.contains(d.pickupNodeId())) allNodeIds.add(d.pickupNodeId());
            if (!allNodeIds.contains(d.dropoffNodeId())) allNodeIds.add(d.dropoffNodeId());
        }

        int n = allNodeIds.size();
        long[][] distanceMatrix = buildDistanceMatrix(allNodeIds, network);
        long[] demands = new long[n];
        for (DeliveryItem d : deliveries) {
            int dropIdx = allNodeIds.indexOf(d.dropoffNodeId());
            if (dropIdx >= 0) demands[dropIdx] = Math.round(d.weightKg());
        }

        try {
            RoutingIndexManager manager = new RoutingIndexManager(n, 1, 0);
            RoutingModel routing = new RoutingModel(manager);

            int transitCb = routing.registerTransitCallback((long from, long to) -> {
                int f = manager.indexToNode(from);
                int t = manager.indexToNode(to);
                return distanceMatrix[f][t];
            });
            routing.setArcCostEvaluatorOfAllVehicles(transitCb);

            int demandCb = routing.registerUnaryTransitCallback(
                    (long idx) -> demands[manager.indexToNode(idx)]);
            routing.addDimensionWithVehicleCapacity(demandCb, 0,
                    new long[]{Math.round(request.vehicleCapacityKg())}, true, "Capacity");

            RoutingDimension capacityDimension = routing.getMutableDimension("Capacity");

            for (DeliveryItem d : deliveries) {
                int pickIdx = allNodeIds.indexOf(d.pickupNodeId());
                int dropIdx = allNodeIds.indexOf(d.dropoffNodeId());
                if (pickIdx >= 0 && dropIdx >= 0 && pickIdx != dropIdx) {
                    long pickupIndex = manager.nodeToIndex(pickIdx);
                    long deliveryIndex = manager.nodeToIndex(dropIdx);
                    
                    routing.addPickupAndDelivery(pickupIndex, deliveryIndex);
                    
                    // Both pickup and delivery must be handled by the same vehicle
                    routing.solver().addConstraint(
                            routing.solver().makeEquality(
                                    routing.vehicleVar(pickupIndex),
                                    routing.vehicleVar(deliveryIndex)));
                                    
                    // Capacity constraint: this is just load, but usually we'd also have a Time dimension for order.
                    // Since we only have Capacity, we ensure the load is consistent.
                }
            }

            RoutingSearchParameters params = main.defaultRoutingSearchParameters().toBuilder()
                    .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                    .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                    .setTimeLimit(Duration.newBuilder().setSeconds(request.timeoutSeconds()).build())
                    .build();

            Assignment solution = routing.solveWithParameters(params);
            long elapsed = System.currentTimeMillis() - startMs;

            if (solution == null) {
                return buildFallbackSolution(request, network, elapsed);
            }

            return extractSolution(routing, manager, solution, allNodeIds, network,
                    elapsed, routing.status());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            return buildFallbackSolution(request, network, elapsed);
        }
    }

    private long[][] buildDistanceMatrix(List<String> nodeIds, RoadNetwork network) {
        int n = nodeIds.size();
        long[][] matrix = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) { matrix[i][j] = 0; continue; }
                Optional<RoadNode> from = network.findNode(RoadNodeId.of(nodeIds.get(i)));
                Optional<RoadNode> to = network.findNode(RoadNodeId.of(nodeIds.get(j)));
                if (from.isPresent() && to.isPresent()) {
                    double dist = from.get().coordinates().haversineDistanceTo(to.get().coordinates());
                    matrix[i][j] = Math.round(dist * 1000);
                } else {
                    matrix[i][j] = 999_999_999;
                }
            }
        }
        return matrix;
    }

    private VrpSolution extractSolution(RoutingModel routing, RoutingIndexManager manager,
                                         Assignment solution, List<String> nodeIds,
                                         RoadNetwork network, long elapsedMs, int status) {
        List<RouteWaypoint> waypoints = new ArrayList<>();
        double totalCost = 0;
        int seq = 0;
        long index = routing.start(0);

        while (!routing.isEnd(index)) {
            int nodeIdx = manager.indexToNode(index);
            String nodeId = nodeIds.get(nodeIdx);
            Optional<RoadNode> node = network.findNode(RoadNodeId.of(nodeId));
            GeoPoint coords = node.map(RoadNode::coordinates).orElse(GeoPoint.of(0, 0));
            WaypointType type = (seq == 0) ? WaypointType.ORIGIN_DEPOT : WaypointType.DELIVERY_POINT;
            waypoints.add(new RouteWaypoint(seq++, nodeId, type, coords, null, null, null, 5));
            long nextIndex = solution.value(routing.nextVar(index));
            totalCost += routing.getArcCostForVehicle(index, nextIndex, 0);
            index = nextIndex;
        }
        int lastIdx = manager.indexToNode(index);
        String lastNodeId = nodeIds.get(lastIdx);
        Optional<RoadNode> lastNode = network.findNode(RoadNodeId.of(lastNodeId));
        waypoints.add(new RouteWaypoint(seq, lastNodeId, WaypointType.ORIGIN_DEPOT,
                lastNode.map(RoadNode::coordinates).orElse(GeoPoint.of(0, 0)),
                null, null, null, 0));

        SolverStatus solverStatus = (status == 1) ? SolverStatus.OPTIMAL : SolverStatus.FEASIBLE;
        return new VrpSolution(waypoints, totalCost / 1000.0, List.of(), solverStatus, elapsedMs, null);
    }

    private VrpSolution buildFallbackSolution(VrpRequest request, RoadNetwork network, long elapsedMs) {
        List<RouteWaypoint> waypoints = new ArrayList<>();
        int seq = 0;
        double totalCost = 0;

        Optional<RoadNode> depot = network.findNode(RoadNodeId.of(request.depotNodeId()));
        GeoPoint depotCoords = depot.map(RoadNode::coordinates).orElse(GeoPoint.of(3.848, 11.502));
        waypoints.add(new RouteWaypoint(seq++, request.depotNodeId(),
                WaypointType.ORIGIN_DEPOT, depotCoords, null, null, null, 0));

        for (DeliveryItem d : request.deliveries()) {
            Optional<RoadNode> pickup = network.findNode(RoadNodeId.of(d.pickupNodeId()));
            if (pickup.isPresent()) {
                waypoints.add(new RouteWaypoint(seq++, d.pickupNodeId(),
                        WaypointType.PICKUP_POINT, pickup.get().coordinates(),
                        d.id(), null, null, 5));
            }
            Optional<RoadNode> drop = network.findNode(RoadNodeId.of(d.dropoffNodeId()));
            if (drop.isPresent()) {
                waypoints.add(new RouteWaypoint(seq++, d.dropoffNodeId(),
                        WaypointType.DELIVERY_POINT, drop.get().coordinates(),
                        d.id(), null, null, 5));
                if (pickup.isPresent()) {
                    totalCost += pickup.get().coordinates()
                            .haversineDistanceTo(drop.get().coordinates());
                }
            }
        }

        waypoints.add(new RouteWaypoint(seq, request.depotNodeId(),
                WaypointType.ORIGIN_DEPOT, depotCoords, null, null, null, 0));

        eventPublisher.publishVrpFallback(
                VrpFallbackActivatedEvent.of(UUID.fromString(request.tenantId()),
                        "OR-Tools solver returned no solution", request.deliveries().size()))
                .subscribe();

        return new VrpSolution(waypoints, totalCost, List.of(),
                SolverStatus.FALLBACK_USED, elapsedMs, null);
    }
}
