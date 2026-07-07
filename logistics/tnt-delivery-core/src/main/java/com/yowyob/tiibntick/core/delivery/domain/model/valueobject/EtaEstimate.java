package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import java.time.Instant;

/**
 * Estimated time of arrival (ETA) for a delivery.
 *
 * <p>Combines:
 * <ul>
 *   <li>An initial log-normal estimate computed at planning time.</li>
 *   <li>A real-time refinement produced by the extended Kalman filter during transit.</li>
 *   <li>A confidence interval [lowerBound, upperBound] communicated to clients.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record EtaEstimate(
        Instant estimatedArrival,
        Instant lowerBound,
        Instant upperBound,
        double confidenceScore,
        double remainingDistanceKm,
        int remainingMinutes
) {

    /**
     * Creates a simple ETA with 80% confidence band (±20% of remaining minutes).
     */
    public static EtaEstimate of(Instant arrival, double distanceKm, int minutes) {
        long slack = Math.max(minutes / 5L, 5L) * 60L;
        return new EtaEstimate(
                arrival,
                arrival.minusSeconds(slack),
                arrival.plusSeconds(slack),
                0.80,
                distanceKm,
                minutes);
    }

    /**
     * Returns {@code true} if this estimate was computed by the Kalman filter
     * (higher confidence threshold).
     */
    public boolean isKalmanRefined() {
        return confidenceScore >= 0.85;
    }
}
