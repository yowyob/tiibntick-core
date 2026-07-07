package com.yowyob.tiibntick.core.resource.domain.event;

import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a FreelancerOrg vehicle is assigned to a delivery mission.
 *
 * <p>Published to Kafka topic: {@code tnt.vehicle.assigned_to_mission} (6 partitions)
 * Consumed by: tnt-delivery-core, tnt-realtime-core
 *
 * @author MANFOUO Braun
 */
public record FreelancerVehicleAssignedToMissionEvent(
        UUID eventId,
        UUID vehicleId,
        UUID ownerOrgId,
        VehicleType vehicleType,
        String missionId,
        Instant occurredAt
) {
    public static FreelancerVehicleAssignedToMissionEvent of(UUID vehicleId, UUID ownerOrgId,
            VehicleType vehicleType, String missionId) {
        return new FreelancerVehicleAssignedToMissionEvent(
                UUID.randomUUID(), vehicleId, ownerOrgId, vehicleType, missionId, Instant.now());
    }
}
