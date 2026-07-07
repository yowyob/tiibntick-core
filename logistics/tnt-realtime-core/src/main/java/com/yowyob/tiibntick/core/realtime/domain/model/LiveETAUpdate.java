package com.yowyob.tiibntick.core.realtime.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing an ETA update computed by the Kalman filter in tnt-route-core.
 * This object is broadcast via WebSocket to all clients subscribed to
 * {@code /topic/delivery/{missionId}}.
 *
 * @author MANFOUO Braun
 */
public record LiveETAUpdate(
        String missionId,
        String delivererId,
        String tenantId,
        String trackingCode,
        GeoCoordinates currentCoordinates,
        ETAInterval etaInterval,
        double remainingDistanceKm,
        int remainingTimeMin,
        double kalmanConfidence,
        LocalDateTime broadcastAt
) {

    public LiveETAUpdate {
        Objects.requireNonNull(missionId, "missionId must not be null");
        Objects.requireNonNull(delivererId, "delivererId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(currentCoordinates, "currentCoordinates must not be null");
        Objects.requireNonNull(etaInterval, "etaInterval must not be null");
        Objects.requireNonNull(broadcastAt, "broadcastAt must not be null");
        if (remainingDistanceKm < 0) throw new IllegalArgumentException("remainingDistanceKm cannot be negative");
        if (remainingTimeMin < 0) throw new IllegalArgumentException("remainingTimeMin cannot be negative");
        if (kalmanConfidence < 0 || kalmanConfidence > 1) {
            throw new IllegalArgumentException("kalmanConfidence must be in [0,1]");
        }
    }

    /**
     * Convenience factory for creating a LiveETAUpdate with the current timestamp.
     */
    public static LiveETAUpdate of(
            String missionId,
            String delivererId,
            String tenantId,
            String trackingCode,
            GeoCoordinates currentCoordinates,
            ETAInterval etaInterval,
            double remainingDistanceKm,
            int remainingTimeMin,
            double kalmanConfidence) {

        return new LiveETAUpdate(
                missionId, delivererId, tenantId, trackingCode,
                currentCoordinates, etaInterval,
                remainingDistanceKm, remainingTimeMin, kalmanConfidence,
                LocalDateTime.now());
    }

    /**
     * Returns the best single ETA (midpoint of the interval).
     *
     * @return best estimate of arrival time
     */
    public LocalDateTime bestEta() {
        return etaInterval.midpoint();
    }

    @Override
    public String toString() {
        return "LiveETAUpdate{mission=" + missionId + ", eta=" + bestEta() + ", remaining=" + remainingDistanceKm + "km}";
    }
}
