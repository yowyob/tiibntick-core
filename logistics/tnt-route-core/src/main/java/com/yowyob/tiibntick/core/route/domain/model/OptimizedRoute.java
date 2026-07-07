package com.yowyob.tiibntick.core.route.domain.model;

import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import java.time.Instant;
import java.util.*;

public final class OptimizedRoute {

    private final List<RouteWaypoint> waypointSequence;
    private final double totalDistanceKm;
    private final int estimatedDurationMinutes;
    private final double compositeCost;
    private final EtaResult etaInitial;
    private final Map<String, Double> costBreakdown;
    private final Instant computedAt;
    private final String algorithm;

    private OptimizedRoute(List<RouteWaypoint> waypointSequence, double totalDistanceKm,
                           int estimatedDurationMinutes, double compositeCost,
                           EtaResult etaInitial, Map<String, Double> costBreakdown,
                           Instant computedAt, String algorithm) {
        this.waypointSequence = Collections.unmodifiableList(new ArrayList<>(waypointSequence));
        this.totalDistanceKm = totalDistanceKm;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.compositeCost = compositeCost;
        this.etaInitial = etaInitial;
        this.costBreakdown = costBreakdown != null ? Map.copyOf(costBreakdown) : Map.of();
        this.computedAt = computedAt;
        this.algorithm = algorithm;
    }

    public static OptimizedRoute create(List<RouteWaypoint> waypoints, double distKm,
                                         int durationMin, double cost, EtaResult eta,
                                         Map<String, Double> breakdown, String algorithm) {
        return new OptimizedRoute(waypoints, distKm, durationMin, cost, eta,
                breakdown, Instant.now(), algorithm);
    }

    public Optional<RouteWaypoint> nextWaypoint(String fromNodeId) {
        for (int i = 0; i < waypointSequence.size() - 1; i++) {
            if (waypointSequence.get(i).nodeId().equals(fromNodeId)) {
                return Optional.of(waypointSequence.get(i + 1));
            }
        }
        return Optional.empty();
    }

    public double residualCostFrom(String nodeId) {
        double totalArcCost = compositeCost;
        for (int i = 0; i < waypointSequence.size(); i++) {
            if (waypointSequence.get(i).nodeId().equals(nodeId)) {
                return totalArcCost * (1.0 - (double) i / waypointSequence.size());
            }
        }
        return totalArcCost;
    }

    public List<RouteWaypoint> waypointSequence() { return waypointSequence; }
    public double totalDistanceKm()               { return totalDistanceKm; }
    public int estimatedDurationMinutes()          { return estimatedDurationMinutes; }
    public double compositeCost()                  { return compositeCost; }
    public EtaResult etaInitial()                  { return etaInitial; }
    public Map<String, Double> costBreakdown()     { return costBreakdown; }
    public Instant computedAt()                    { return computedAt; }
    public String algorithm()                      { return algorithm; }

    @Override
    public String toString() {
        return "OptimizedRoute{waypoints=" + waypointSequence.size()
                + ", dist=" + totalDistanceKm + "km, cost=" + compositeCost
                + ", algo=" + algorithm + "}";
    }
}
