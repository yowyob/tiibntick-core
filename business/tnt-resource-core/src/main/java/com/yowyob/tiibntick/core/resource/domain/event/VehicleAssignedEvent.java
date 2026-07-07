package com.yowyob.tiibntick.core.resource.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a vehicle is assigned to a deliverer.
 * @author MANFOUO Braun.
 */
public record VehicleAssignedEvent(
        UUID eventId,
        UUID vehicleId,
        UUID tenantId,
        UUID agencyId,
        UUID delivererId,
        UUID missionId,
        Instant occurredAt
) {
    public static VehicleAssignedEvent of(UUID vehicleId, UUID tenantId, UUID agencyId,
            UUID delivererId, UUID missionId) {
        return new VehicleAssignedEvent(UUID.randomUUID(), vehicleId, tenantId, agencyId,
                delivererId, missionId, Instant.now());
    }
}
