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
        Instant occurredAt,
        /**
         * Actor owning a personal vehicle (independent courier, no organisation), else {@code null}. Added
         * 2026-11-28 (decision D-184): an independent's vehicle has no {@code ownerOrgId}.
         */
        UUID ownerActorId
) {
    public static FreelancerVehicleAssignedToMissionEvent of(UUID vehicleId, UUID ownerOrgId,
            VehicleType vehicleType, String missionId) {
        return of(vehicleId, ownerOrgId, null, vehicleType, missionId);
    }

    public static FreelancerVehicleAssignedToMissionEvent of(UUID vehicleId, UUID ownerOrgId, UUID ownerActorId,
            VehicleType vehicleType, String missionId) {
        return new FreelancerVehicleAssignedToMissionEvent(
                UUID.randomUUID(), vehicleId, ownerOrgId, vehicleType, missionId, Instant.now(), ownerActorId);
    }
}
