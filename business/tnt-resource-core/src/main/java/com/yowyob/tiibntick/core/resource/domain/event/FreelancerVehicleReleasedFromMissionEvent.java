package com.yowyob.tiibntick.core.resource.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a FreelancerOrg vehicle is released from its mission
 * (mission completed, cancelled, or reassigned).
 *
 * <p>Published to Kafka topic: {@code tnt.vehicle.released_from_mission} (6 partitions)
 * Consumed by: tnt-delivery-core
 *
 * @author MANFOUO Braun
 */
public record FreelancerVehicleReleasedFromMissionEvent(
        UUID eventId,
        UUID vehicleId,
        UUID ownerOrgId,
        String missionId,
        Instant occurredAt,
        /**
         * Actor owning a personal vehicle (independent courier, no organisation), else {@code null}. Added
         * 2026-11-28 (decision D-184): an independent's vehicle has no {@code ownerOrgId}.
         */
        UUID ownerActorId
) {
    public static FreelancerVehicleReleasedFromMissionEvent of(UUID vehicleId, UUID ownerOrgId,
            String missionId) {
        return of(vehicleId, ownerOrgId, null, missionId);
    }

    public static FreelancerVehicleReleasedFromMissionEvent of(UUID vehicleId, UUID ownerOrgId, UUID ownerActorId,
            String missionId) {
        return new FreelancerVehicleReleasedFromMissionEvent(
                UUID.randomUUID(), vehicleId, ownerOrgId, missionId, Instant.now(), ownerActorId);
    }
}
