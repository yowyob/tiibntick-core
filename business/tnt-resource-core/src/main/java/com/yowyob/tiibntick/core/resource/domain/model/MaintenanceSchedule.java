package com.yowyob.tiibntick.core.resource.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Value Object representing a planned maintenance appointment for a vehicle.
 *
 * @author MANFOUO Braun.
 */
public record MaintenanceSchedule(
        LocalDate scheduledDate,
        MaintenanceType type,
        String reason,
        Double odometerThresholdKm
) {

    public MaintenanceSchedule {
        Objects.requireNonNull(scheduledDate, "scheduledDate is required");
        Objects.requireNonNull(type, "type is required");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    /**
     * Returns true if this maintenance is due based on the provided current odometer reading.
     */
    public boolean isDueByOdometer(double currentOdometerKm) {
        return odometerThresholdKm != null && currentOdometerKm >= odometerThresholdKm;
    }

    /**
     * Returns true if this maintenance is scheduled on or before the given date.
     */
    public boolean isDueByDate(LocalDate currentDate) {
        return !scheduledDate.isAfter(currentDate);
    }
}
