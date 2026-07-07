package com.yowyob.tiibntick.core.realtime.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing a rerouting alert emitted when the hysteresis
 * threshold (ε = 15% of the initial route cost) has been exceeded and the
 * VRP solver in tnt-route-core has computed a new optimal route.
 *
 * <p>This alert is broadcast to the deliverer and the dispatching agency.</p>
 *
 * @author MANFOUO Braun
 */
public record ReroutingAlert(
        String missionId,
        String delivererId,
        String tenantId,
        String oldRouteId,
        String newRouteId,
        double costImprovement,
        double distanceSavedKm,
        int timeSavedMin,
        String reason,
        LocalDateTime triggeredAt
) {

    public ReroutingAlert {
        Objects.requireNonNull(missionId, "missionId must not be null");
        Objects.requireNonNull(delivererId, "delivererId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(newRouteId, "newRouteId must not be null");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
    }

    public static ReroutingAlert of(
            String missionId, String delivererId, String tenantId,
            String oldRouteId, String newRouteId,
            double costImprovement, double distanceSavedKm, int timeSavedMin, String reason) {
        return new ReroutingAlert(
                missionId, delivererId, tenantId,
                oldRouteId, newRouteId,
                costImprovement, distanceSavedKm, timeSavedMin,
                reason, LocalDateTime.now());
    }

    public boolean isSignificant() {
        return costImprovement >= 0.15; // hysteresis threshold of 15%
    }

    @Override
    public String toString() {
        return "ReroutingAlert{mission=" + missionId + ", improvement=" + String.format("%.1f%%", costImprovement * 100) + "}";
    }
}
