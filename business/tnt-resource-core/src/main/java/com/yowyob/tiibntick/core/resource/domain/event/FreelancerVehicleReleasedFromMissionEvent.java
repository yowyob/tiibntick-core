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
        Instant occurredAt
) {
    public static FreelancerVehicleReleasedFromMissionEvent of(UUID vehicleId, UUID ownerOrgId,
            String missionId) {
        return new FreelancerVehicleReleasedFromMissionEvent(
                UUID.randomUUID(), vehicleId, ownerOrgId, missionId, Instant.now());
    }
}
