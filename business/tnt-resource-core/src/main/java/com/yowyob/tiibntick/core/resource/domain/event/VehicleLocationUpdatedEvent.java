package com.yowyob.tiibntick.core.resource.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a vehicle's GPS location is updated.
 * Consumed by tnt-realtime-core for live tracking.
 * @author MANFOUO Braun.
 */
public record VehicleLocationUpdatedEvent(
        UUID eventId,
        UUID vehicleId,
        UUID tenantId,
        UUID agencyId,
        double latitude,
        double longitude,
        Instant occurredAt
) {
    public static VehicleLocationUpdatedEvent of(UUID vehicleId, UUID tenantId, UUID agencyId,
            double latitude, double longitude) {
        return new VehicleLocationUpdatedEvent(UUID.randomUUID(), vehicleId, tenantId, agencyId,
                latitude, longitude, Instant.now());
    }
}
