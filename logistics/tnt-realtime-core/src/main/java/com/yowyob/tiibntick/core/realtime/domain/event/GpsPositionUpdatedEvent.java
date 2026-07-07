package com.yowyob.tiibntick.core.realtime.domain.event;

import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;

/**
 * Emitted after a valid GPS ping has been processed and the actor's position
 * updated. Consumed by:
 * <ul>
 *   <li>{@code tnt-route-core} — Kalman filter ETA recomputation</li>
 *   <li>{@code tnt-geo-core} — map-matching</li>
 *   <li>{@code tnt-incident-core} — anomaly-triggered incident creation</li>
 * </ul>
 *
 * <p><strong>Incident-core enrichment:</strong> The fields {@code trajectoryAnomaly},
 * {@code prolongedStop}, and {@code stopDurationMinutes} are computed by the
 * {@code GpsPingProcessor} domain service before publication. When
 * {@code trajectoryAnomaly = true} or {@code prolongedStop = true},
 * tnt-incident-core will auto-create a {@code SYSTEM_INFRASTRUCTURE} or
 * {@code SLA_TIME} type incident respectively.</p>
 *
 * @author MANFOUO Braun
 */
public class GpsPositionUpdatedEvent extends RealtimeDomainEvent {

    private static final String TOPIC = "tnt.realtime.gps.position.updated";

    private final String delivererId;
    private final String missionId;
    private final String agencyId;
    private final GeoCoordinates coordinates;
    private final double speedKmh;
    private final double bearing;
    private final double accuracy;

    /**
     * Whether the current trajectory shows a significant anomaly
     * (abrupt direction change, GPS spoofing pattern, or speed inconsistency)
     * detected by the Kalman filter divergence check.
     * Consumed by tnt-incident-core to auto-create GPS_SPOOFING or
     * TRAJECTORY_DEVIATION incidents.
     */
    private final boolean trajectoryAnomaly;

    /**
     * Whether the deliverer has been stationary for longer than the
     * configured stop threshold (default 10 minutes during an active mission).
     * Consumed by tnt-incident-core to auto-create a PROLONGED_STOP incident.
     */
    private final boolean prolongedStop;

    /**
     * Duration in minutes the deliverer has been stopped at the current position.
     * Populated only when {@code prolongedStop = true}; zero otherwise.
     */
    private final int stopDurationMinutes;

    /**
     * Full constructor including all anomaly detection fields.
     * Used by {@code GpsPingProcessor} after running the anomaly detection pipeline.
     *
     * @param tenantId            tenant context
     * @param delivererId         actor performing the delivery
     * @param missionId           active mission (nullable if deliverer is idle)
     * @param agencyId            agency owning this deliverer (nullable for freelancers)
     * @param coordinates         current GPS position (post-Kalman filtered)
     * @param speedKmh            current speed in km/h
     * @param bearing             heading in degrees [0, 360]
     * @param accuracy            GPS accuracy in meters (lower = better)
     * @param trajectoryAnomaly   true if trajectory divergence detected
     * @param prolongedStop       true if deliverer stopped > threshold
     * @param stopDurationMinutes minutes the deliverer has been stopped
     */
    public GpsPositionUpdatedEvent(String tenantId,
                                    String delivererId,
                                    String missionId,
                                    String agencyId,
                                    GeoCoordinates coordinates,
                                    double speedKmh,
                                    double bearing,
                                    double accuracy,
                                    boolean trajectoryAnomaly,
                                    boolean prolongedStop,
                                    int stopDurationMinutes) {
        super(tenantId);
        this.delivererId = delivererId;
        this.missionId = missionId;
        this.agencyId = agencyId;
        this.coordinates = coordinates;
        this.speedKmh = speedKmh;
        this.bearing = bearing;
        this.accuracy = accuracy;
        this.trajectoryAnomaly = trajectoryAnomaly;
        this.prolongedStop = prolongedStop;
        this.stopDurationMinutes = stopDurationMinutes;
    }

    /**
     * Backward-compatible constructor without anomaly fields.
     * Used when the deliverer has no active mission or anomaly computation is skipped.
     *
     * @param tenantId    tenant context
     * @param delivererId actor performing the delivery
     * @param missionId   active mission (nullable)
     * @param coordinates current GPS position
     * @param speedKmh    current speed in km/h
     * @param bearing     heading in degrees
     * @param accuracy    GPS accuracy in meters
     */
    public GpsPositionUpdatedEvent(String tenantId,
                                    String delivererId,
                                    String missionId,
                                    GeoCoordinates coordinates,
                                    double speedKmh,
                                    double bearing,
                                    double accuracy) {
        this(tenantId, delivererId, missionId, null,
                coordinates, speedKmh, bearing, accuracy,
                false, false, 0);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getDelivererId()       { return delivererId; }
    public String getMissionId()         { return missionId; }
    public String getAgencyId()          { return agencyId; }
    public GeoCoordinates getCoordinates() { return coordinates; }
    public double getSpeedKmh()          { return speedKmh; }
    public double getBearing()           { return bearing; }
    public double getAccuracy()          { return accuracy; }
    public boolean isTrajectoryAnomaly() { return trajectoryAnomaly; }
    public boolean isProlongedStop()     { return prolongedStop; }
    public int getStopDurationMinutes()  { return stopDurationMinutes; }

    @Override
    public String kafkaTopic() {
        return TOPIC;
    }
}
