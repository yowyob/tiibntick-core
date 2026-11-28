package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

/**
 * One delivery threshold set by the TiiBnTick administrator (platform value) and overridable per TiiBnTick Agency
 * organization. Single source of truth for the default value, the accepted range, the JSON field name and the
 * database column of each threshold.
 *
 * <p>Defaults are the user's decisions of 2026-11-26 ({@code decisions-evenements-colis-livraison.md}): [B.5]
 * (3 attempts, 7 days of relay storage, reminder the day before), QO-8 (3 failed returns before storage and
 * escalation to support) and QO-18 (late alerts: pickup window end + 15 min, delivery window end, ETA + 30 min, GPS
 * lost 10 min, prolonged stop 20 min). The per-urgency fallback delay of a late delivery without a window reuses the
 * values of {@code TntSettingsRegistrar} ({@code tnt.delivery.sla.<level>.hours}: 48 / 24 / 8 h).
 *
 * <p>Ranges: attempts, storage days and reminder come from the target specification (§5.1); the other ranges are
 * technical sanity bounds of lot L1 (reported as such to the maintainer).
 */
public enum DeliveryThreshold {

    /** [B.5] Delivery attempts before the delivery fails for good and the parcel goes back. */
    MAX_DELIVERY_ATTEMPTS("maxDeliveryAttempts", "max_delivery_attempts", Unit.COUNT, 3, 1, 10),
    /** [B.5] Days a parcel stays at the destination relay before it goes back to the sender. */
    RELAY_STORAGE_DAYS("relayStorageDays", "relay_storage_days", Unit.DAYS, 7, 1, 60),
    /** [B.5] Days before the end of storage the recipient is reminded (0 = no reminder). */
    RELAY_STORAGE_REMINDER_DAYS_BEFORE("relayStorageReminderDaysBefore", "relay_storage_reminder_days_before",
            Unit.DAYS, 1, 0, 59),
    /** QO-8: failed return deliveries before the parcel is stored and escalated to support. */
    MAX_FAILED_RETURNS("maxFailedReturns", "max_failed_returns", Unit.COUNT, 3, 1, 10),
    /** QO-18 {@code PICKUP_LATE}: minutes after the end of the pickup window. */
    PICKUP_LATE_GRACE_MINUTES("pickupLateGraceMinutes", "pickup_late_grace_minutes", Unit.MINUTES, 15, 0, 1440),
    /** QO-18 {@code DELIVERY_LATE}: minutes after the end of the delivery window (0 = at the end of the window). */
    DELIVERY_LATE_GRACE_MINUTES("deliveryLateGraceMinutes", "delivery_late_grace_minutes", Unit.MINUTES, 0, 0, 1440),
    /** QO-18 {@code DELIVERY_LATE} without a window, urgency STANDARD: hours after creation. */
    DELIVERY_LATE_FALLBACK_HOURS_STANDARD("deliveryLateFallbackHoursStandard", "delivery_late_fallback_hours_standard",
            Unit.HOURS, 48, 1, 720),
    /** QO-18 {@code DELIVERY_LATE} without a window, urgency EXPRESS: hours after creation. */
    DELIVERY_LATE_FALLBACK_HOURS_EXPRESS("deliveryLateFallbackHoursExpress", "delivery_late_fallback_hours_express",
            Unit.HOURS, 24, 1, 720),
    /** QO-18 {@code DELIVERY_LATE} without a window, urgency SAME_DAY: hours after creation. */
    DELIVERY_LATE_FALLBACK_HOURS_SAME_DAY("deliveryLateFallbackHoursSameDay", "delivery_late_fallback_hours_same_day",
            Unit.HOURS, 8, 1, 720),
    /** QO-18 {@code ETA_EXCEEDED}: minutes past the announced ETA. */
    ETA_EXCEEDED_GRACE_MINUTES("etaExceededGraceMinutes", "eta_exceeded_grace_minutes", Unit.MINUTES, 30, 0, 1440),
    /** QO-18 {@code GPS_SIGNAL_LOST}: minutes without a position. */
    GPS_SIGNAL_LOST_MINUTES("gpsSignalLostMinutes", "gps_signal_lost_minutes", Unit.MINUTES, 10, 1, 1440),
    /** QO-18 {@code PROLONGED_STOP}: minutes stopped. */
    PROLONGED_STOP_MINUTES("prolongedStopMinutes", "prolonged_stop_minutes", Unit.MINUTES, 20, 1, 1440);

    /** Unit of a threshold value. */
    public enum Unit { COUNT, DAYS, HOURS, MINUTES }

    private final String field;
    private final String column;
    private final Unit unit;
    private final int defaultValue;
    private final int min;
    private final int max;

    DeliveryThreshold(String field, String column, Unit unit, int defaultValue, int min, int max) {
        this.field = field;
        this.column = column;
        this.unit = unit;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    /** JSON / API field name. */
    public String field() { return field; }

    /** Database column (same name in the platform and the organization tables). */
    public String column() { return column; }

    public Unit unit() { return unit; }

    public int defaultValue() { return defaultValue; }

    public int min() { return min; }

    public int max() { return max; }

    public boolean accepts(int value) {
        return value >= min && value <= max;
    }
}
