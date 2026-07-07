package com.yowyob.tiibntick.core.resource.domain.event;

import com.yowyob.tiibntick.core.resource.domain.model.MaintenanceType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event emitted when a maintenance alert is triggered for a vehicle.
 * Consumed by tnt-notify-core to send alerts to fleet managers.
 * @author MANFOUO Braun.
 */
public record MaintenanceAlertTriggeredEvent(
        UUID eventId,
        UUID vehicleId,
        UUID tenantId,
        UUID agencyId,
        String registrationNumber,
        MaintenanceType maintenanceType,
        LocalDate scheduledDate,
        double currentOdometerKm,
        Instant occurredAt
) {
    public static MaintenanceAlertTriggeredEvent of(UUID vehicleId, UUID tenantId, UUID agencyId,
            String registrationNumber, MaintenanceType type, LocalDate scheduledDate,
            double currentOdometerKm) {
        return new MaintenanceAlertTriggeredEvent(UUID.randomUUID(), vehicleId, tenantId, agencyId,
                registrationNumber, type, scheduledDate, currentOdometerKm, Instant.now());
    }
}
