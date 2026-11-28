package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Detections on a delivery (spec §3.5, decisions [C.3] and [D.1]): they raise {@code DeliveryAlertRaised} on
 * {@code tnt.delivery.events} and reach the support, never an incident (only the courier declares an incident).
 * Thresholds: decision QO-18 ({@code tnt.delivery.alerts.*}).
 */
public enum DeliveryAlertType {

    /** End of the pickup window + grace exceeded, parcel not collected. */
    PICKUP_LATE(true),
    /** End of the delivery window exceeded (else the urgency delay) — replaces {@code SLA_BREACHED}. */
    DELIVERY_LATE(true),
    /** ETA + grace exceeded — replaces {@code TIMED_OUT}. */
    ETA_EXCEEDED(true),
    /** No GPS position of the courier for the configured delay. */
    GPS_SIGNAL_LOST(false),
    /** Trajectory anomaly flagged by the realtime GPS stream. */
    TRAJECTORY_DEVIATION(false),
    /** Prolonged stop flagged by the realtime GPS stream. */
    PROLONGED_STOP(false),
    /** The courier crossed a danger or restricted geofence. */
    GEOFENCE_EXIT(false),
    /** Relay storage reminder, day before the end of custody (spec §5.2). */
    RELAY_STORAGE_EXPIRING(false);

    private final boolean lateness;

    DeliveryAlertType(boolean lateness) {
        this.lateness = lateness;
    }

    /** @return whether the alert marks the delivery as late ({@code late}, {@code lateSince}) */
    public boolean isLateness() {
        return lateness;
    }
}
