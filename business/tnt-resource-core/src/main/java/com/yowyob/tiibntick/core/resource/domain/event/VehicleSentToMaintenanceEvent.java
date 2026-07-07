package com.yowyob.tiibntick.core.resource.domain.event;

import com.yowyob.tiibntick.core.resource.domain.model.MaintenanceType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event emitted when a vehicle is sent to maintenance.
 * @author MANFOUO Braun.
 */
public record VehicleSentToMaintenanceEvent(
        UUID eventId,
        UUID vehicleId,
        UUID tenantId,
        UUID agencyId,
        MaintenanceType maintenanceType,
        LocalDate scheduledDate,
        Instant occurredAt
) {
    public static VehicleSentToMaintenanceEvent of(UUID vehicleId, UUID tenantId, UUID agencyId,
            MaintenanceType type, LocalDate scheduledDate) {
        return new VehicleSentToMaintenanceEvent(UUID.randomUUID(), vehicleId, tenantId, agencyId,
                type, scheduledDate, Instant.now());
    }
}
