package com.yowyob.tiibntick.core.resource.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a vehicle is permanently retired from the fleet.
 * @author MANFOUO Braun.
 */
public record VehicleRetiredEvent(
        UUID eventId,
        UUID vehicleId,
        UUID tenantId,
        UUID agencyId,
        Instant occurredAt
) {
    public static VehicleRetiredEvent of(UUID vehicleId, UUID tenantId, UUID agencyId) {
        return new VehicleRetiredEvent(UUID.randomUUID(), vehicleId, tenantId, agencyId, Instant.now());
    }
}
