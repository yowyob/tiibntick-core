package com.yowyob.tiibntick.core.resource.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a vehicle is released from an assignment.
 * @author MANFOUO Braun.
 */
public record VehicleUnassignedEvent(
        UUID eventId,
        UUID vehicleId,
        UUID tenantId,
        UUID agencyId,
        UUID previousDelivererId,
        Instant occurredAt
) {
    public static VehicleUnassignedEvent of(UUID vehicleId, UUID tenantId, UUID agencyId,
            UUID previousDelivererId) {
        return new VehicleUnassignedEvent(UUID.randomUUID(), vehicleId, tenantId, agencyId,
                previousDelivererId, Instant.now());
    }
}
