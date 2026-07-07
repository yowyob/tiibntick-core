package com.yowyob.tiibntick.core.route.domain.model;

import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import java.util.*;

public final class RoutePath {

    private final List<RoadNodeId> nodeSequence;
    private final List<RouteSegment> segments;
    private final double totalDistanceKm;
    private final double totalCompositeCost;

    private RoutePath(List<RoadNodeId> nodeSequence, List<RouteSegment> segments,
                      double totalDistanceKm, double totalCompositeCost) {
        this.nodeSequence = Collections.unmodifiableList(new ArrayList<>(nodeSequence));
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        this.totalDistanceKm = totalDistanceKm;
        this.totalCompositeCost = totalCompositeCost;
    }

    public static RoutePath of(List<RoadNodeId> nodes, List<RouteSegment> segments,
                               double distKm, double cost) {
        return new RoutePath(nodes, segments, distKm, cost);
    }

    public static RoutePath empty() {
        return new RoutePath(List.of(), List.of(), 0.0, 0.0);
    }

    public boolean isEmpty() { return nodeSequence.isEmpty(); }
    public int nodeCount() { return nodeSequence.size(); }
    public List<RoadNodeId> nodeSequence() { return nodeSequence; }
    public List<RouteSegment> segments() { return segments; }
    public double totalDistanceKm() { return totalDistanceKm; }
    public double totalCompositeCost() { return totalCompositeCost; }

    public RoadNodeId origin() { return nodeSequence.isEmpty() ? null : nodeSequence.getFirst(); }
    public RoadNodeId destination() { return nodeSequence.isEmpty() ? null : nodeSequence.getLast(); }

    public double residualCostFrom(RoadNodeId nodeId) {
        double residual = 0;
        boolean found = false;
        for (RouteSegment seg : segments) {
            if (found) residual += seg.partialCost();
            if (seg.fromNodeId().equals(nodeId)) found = true;
        }
        return found ? residual : totalCompositeCost;
    }

    @Override
    public String toString() {
        return "RoutePath{nodes=" + nodeCount() + ", dist=" + totalDistanceKm
                + "km, cost=" + totalCompositeCost + "}";
    }
}
