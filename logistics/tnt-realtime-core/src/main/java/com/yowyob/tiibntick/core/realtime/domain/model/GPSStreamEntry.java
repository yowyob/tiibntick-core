package com.yowyob.tiibntick.core.realtime.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing a single GPS position update (ping) from a deliverer's device.
 * Ingested at a rate of one ping every 10–30 seconds during an active mission.
 *
 * <p>Contains the raw sensor data before any map-matching or Kalman filtering.</p>
 *
 * @author MANFOUO Braun
 */
public record GPSStreamEntry(
        String delivererId,
        String missionId,
        String tenantId,
        GeoCoordinates coordinates,
        double speedKmh,
        double bearing,
        double accuracy,
        Integer batteryLevel,
        LocalDateTime timestamp,

        // ── : FreelancerOrg fleet tracking ───────────────────────────────

        /**
         * UUID of the FreelancerOrganization this deliverer belongs to.
         * Null when the deliverer is an independent freelancer or agency employee.
         * References tnt-organization-core UUID — pure integration key.
         *
         * <p>When non-null, this ping is broadcast to the FreelancerOrg sub-deliverer
         * tracking topic: {@code /topic/fleet/{freelancerOrgId}}.
         */
        String freelancerOrgId
) {

    /** Maximum speed (km/h) a deliverer is expected to travel. Used for outlier detection. */
    private static final double MAX_REALISTIC_SPEED_KMH = 150.0;

    /** Maximum jump distance (km) between two consecutive valid GPS points (30s interval). */
    private static final double MAX_JUMP_DISTANCE_KM = MAX_REALISTIC_SPEED_KMH / 120.0; // 1.25 km in 30s

    public GPSStreamEntry {
        Objects.requireNonNull(delivererId, "delivererId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (speedKmh < 0) throw new IllegalArgumentException("Speed cannot be negative");
        if (bearing < 0 || bearing > 360) throw new IllegalArgumentException("Bearing must be in [0,360]");
        if (accuracy < 0) throw new IllegalArgumentException("Accuracy cannot be negative");
        // freelancerOrgId is nullable — null for non-org deliverers
    }

    /**
     * Convenience factory for non-FreelancerOrg GPS entries (backward compat).
     */
    public static GPSStreamEntry of(String delivererId, String missionId, String tenantId,
            GeoCoordinates coordinates, double speedKmh, double bearing,
            double accuracy, Integer batteryLevel, LocalDateTime timestamp) {
        return new GPSStreamEntry(delivererId, missionId, tenantId, coordinates,
                speedKmh, bearing, accuracy, batteryLevel, timestamp, null);
    }

    /** Returns true if this entry is from a FreelancerOrg sub-deliverer. */
    public boolean hasFreelancerOrg() {
        return freelancerOrgId != null && !freelancerOrgId.isBlank();
    }

    /**
     * Validates this GPS ping as a plausible reading.
     * Rejects entries with invalid coordinates, excessive speed, or very poor accuracy.
     *
     * @return true if this entry passes basic validity checks
     */
    public boolean isValid() {
        return coordinates.isValid()
                && speedKmh <= MAX_REALISTIC_SPEED_KMH
                && accuracy <= 500.0; // 500m accuracy threshold
    }

    /**
     * Detects whether this ping is a spatial outlier relative to the previous ping.
     * Uses a simple max-jump-distance heuristic: if the distance between the two
     * readings exceeds the maximum possible in the elapsed interval, it is an outlier.
     *
     * @param previous the previous valid GPS entry (may be null for the first ping)
     * @return true if this entry appears to be a GPS outlier
     */
    public boolean isOutlier(GPSStreamEntry previous) {
        if (previous == null) return false;
        double distanceKm = coordinates.distanceKmTo(previous.coordinates);
        long elapsedSeconds = java.time.Duration.between(previous.timestamp, this.timestamp).abs().toSeconds();
        if (elapsedSeconds == 0) return distanceKm > 0.01; // 10m in 0s = outlier
        double maxAllowedKm = (MAX_REALISTIC_SPEED_KMH / 3600.0) * elapsedSeconds;
        return distanceKm > maxAllowedKm * 1.2; // 20% tolerance factor
    }

    public boolean hasMission() {
        return missionId != null && !missionId.isBlank();
    }

    public boolean hasBatteryInfo() {
        return batteryLevel != null;
    }

    @Override
    public String toString() {
        return "GPSStreamEntry{deliverer=" + delivererId + ", coords=" + coordinates + ", speed=" + speedKmh + "km/h}";
    }
}
