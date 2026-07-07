package com.yowyob.tiibntick.core.route.domain.model;

import java.util.*;

public record VrpSolution(
        List<RouteWaypoint> orderedWaypoints,
        double totalCost,
        List<String> relaysUsed,
        SolverStatus solverStatus,
        long computationTimeMs,
        Double gap
) {
    public boolean isOptimal() { return solverStatus == SolverStatus.OPTIMAL; }
    public boolean isFallback() { return solverStatus == SolverStatus.FALLBACK_USED; }
    public boolean isFeasible() { return solverStatus != SolverStatus.INFEASIBLE; }
    public int stopCount() { return orderedWaypoints != null ? orderedWaypoints.size() : 0; }
}
