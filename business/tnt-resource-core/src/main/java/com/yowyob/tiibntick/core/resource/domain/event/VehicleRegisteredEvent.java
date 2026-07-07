package com.yowyob.tiibntick.core.resource.domain.event;

import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new vehicle is registered in the TiiBnTick fleet.
 * @author MANFOUO Braun.
 */
public record VehicleRegisteredEvent(
        UUID eventId,
        UUID vehicleId,
        UUID tenantId,
        UUID agencyId,
        String registrationNumber,
        VehicleType vehicleType,
        double maxWeightKg,
        double maxVolumeM3,
        Instant occurredAt
) {
    public static VehicleRegisteredEvent of(UUID vehicleId, UUID tenantId, UUID agencyId,
            String registrationNumber, VehicleType vehicleType, double maxWeightKg, double maxVolumeM3) {
        return new VehicleRegisteredEvent(UUID.randomUUID(), vehicleId, tenantId, agencyId,
                registrationNumber, vehicleType, maxWeightKg, maxVolumeM3, Instant.now());
    }
}
