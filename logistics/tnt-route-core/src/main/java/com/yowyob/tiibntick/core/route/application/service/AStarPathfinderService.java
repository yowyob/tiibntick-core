package com.yowyob.tiibntick.core.route.application.service;

import com.yowyob.tiibntick.core.geo.application.service.CostFunctionService;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import com.yowyob.tiibntick.core.route.application.port.in.IComputeShortestPathUseCase;
import com.yowyob.tiibntick.core.route.application.port.out.IRoadNetworkProvider;
import com.yowyob.tiibntick.core.route.domain.exception.PathNotFoundException;
import com.yowyob.tiibntick.core.route.domain.model.RoutePath;
import com.yowyob.tiibntick.core.route.domain.model.RouteSegment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@Service
public class AStarPathfinderService implements IComputeShortestPathUseCase {

    private static final double V_MAX_KMH = 80.0;

    private final IRoadNetworkProvider networkProvider;
    private final CostFunctionService costFunctionService;

    public AStarPathfinderService(IRoadNetworkProvider networkProvider,
                                  CostFunctionService costFunctionService) {
        this.networkProvider = networkProvider;
        this.costFunctionService = costFunctionService;
    }

    @Override
    public Mono<RoutePath> findShortestPath(String originNodeId, String destinationNodeId,
                                             CostWeights weights, UUID tenantId) {
        return networkProvider.loadNetwork(tenantId)
                .map(network -> {
                    WeatherCondition weather = WeatherCondition.clear(Instant.now());
                    return computeAStar(network, RoadNodeId.of(originNodeId),
                            RoadNodeId.of(destinationNodeId), weights, weather);
                });
    }

    public RoutePath computeAStar(RoadNetwork network, RoadNodeId origin, RoadNodeId destination,
                                   CostWeights weights, WeatherCondition weather) {
        if (!network.containsNode(origin))
            throw new PathNotFoundException(origin.value(), destination.value());
        if (!network.containsNode(destination))
            throw new PathNotFoundException(origin.value(), destination.value());
        if (origin.equals(destination))
            return RoutePath.of(List.of(origin), List.of(), 0.0, 0.0);

        RoadNode destNode = network.findNode(destination).orElseThrow();

        Map<RoadNodeId, Double> gScore = new HashMap<>();
        Map<RoadNodeId, Double> fScore = new HashMap<>();
        Map<RoadNodeId, RoadNodeId> cameFrom = new HashMap<>();
        Map<RoadNodeId, RoadArc> cameFromArc = new HashMap<>();

        Comparator<RoadNodeId> comparator = Comparator.comparingDouble(
                n -> fScore.getOrDefault(n, Double.MAX_VALUE));
        PriorityQueue<RoadNodeId> openSet = new PriorityQueue<>(comparator);

        gScore.put(origin, 0.0);
        fScore.put(origin, heuristic(network, origin, destNode));
        openSet.add(origin);

        Set<RoadNodeId> closedSet = new HashSet<>();

        while (!openSet.isEmpty()) {
            RoadNodeId current = openSet.poll();

            if (current.equals(destination)) {
                return reconstructPath(network, cameFrom, cameFromArc, current, gScore.get(current));
            }

            if (closedSet.contains(current)) continue;
            closedSet.add(current);

            List<RoadArc> neighbors = network.outgoingArcs(current);
            for (RoadArc arc : neighbors) {
                RoadNodeId neighbor = arc.targetId();
                if (closedSet.contains(neighbor)) continue;

                double arcCost = costFunctionService.computeCost(arc, weights, weather);
                double tentativeG = gScore.getOrDefault(current, Double.MAX_VALUE) + arcCost;

                if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    cameFromArc.put(neighbor, arc);
                    gScore.put(neighbor, tentativeG);
                    fScore.put(neighbor, tentativeG + heuristic(network, neighbor, destNode));
                    openSet.remove(neighbor);
                    openSet.add(neighbor);
                }
            }
        }

        throw new PathNotFoundException(origin.value(), destination.value());
    }

    private double heuristic(RoadNetwork network, RoadNodeId nodeId, RoadNode destination) {
        Optional<RoadNode> nodeOpt = network.findNode(nodeId);
        if (nodeOpt.isEmpty()) return 0.0;
        double distKm = nodeOpt.get().coordinates().haversineDistanceTo(destination.coordinates());
        return distKm / V_MAX_KMH;
    }

    private RoutePath reconstructPath(RoadNetwork network,
                                       Map<RoadNodeId, RoadNodeId> cameFrom,
                                       Map<RoadNodeId, RoadArc> cameFromArc,
                                       RoadNodeId current, double totalCost) {
        List<RoadNodeId> nodes = new ArrayList<>();
        List<RouteSegment> segments = new ArrayList<>();
        double totalDist = 0;

        nodes.add(current);
        while (cameFrom.containsKey(current)) {
            RoadNodeId prev = cameFrom.get(current);
            RoadArc arc = cameFromArc.get(current);
            segments.addFirst(new RouteSegment(prev, current, arc.id(),
                    arc.distanceKm(), arc.travelTimeHours()));
            totalDist += arc.distanceKm();
            current = prev;
            nodes.addFirst(current);
        }

        return RoutePath.of(nodes, segments, totalDist, totalCost);
    }
}
