package com.yowyob.tiibntick.core.route.domain.model;

import java.time.Instant;

public record ReroutingDecision(
        String missionId,
        double currentResidualCost,
        double alternativeCost,
        double hysteresisThreshold,
        double switchingCost,
        ReroutingChoice decision,
        Instant decidedAt
) {
    public boolean shouldReroute() {
        return decision == ReroutingChoice.REROUTE;
    }

    public double costImprovement() {
        return currentResidualCost - alternativeCost;
    }

    public static ReroutingDecision evaluate(String missionId,
            double currentCost, double newCost, double initialCost,
            double switchCost) {
        double epsilon = 0.15 * initialCost;
        ReroutingChoice choice;
        if (currentCost > newCost + epsilon + switchCost) {
            choice = ReroutingChoice.REROUTE;
        } else {
            choice = ReroutingChoice.KEEP_CURRENT;
        }
        return new ReroutingDecision(missionId, currentCost, newCost,
                epsilon, switchCost, choice, Instant.now());
    }
}
