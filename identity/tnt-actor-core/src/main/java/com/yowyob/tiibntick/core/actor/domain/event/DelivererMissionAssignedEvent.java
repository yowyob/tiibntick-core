package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

public record DelivererMissionAssignedEvent(
        UUID eventId,
        UUID delivererActorId,
        UUID tenantId,
        UUID agencyId,
        UUID branchId,
        UUID missionId,
        Instant occurredAt) {

    public static DelivererMissionAssignedEvent of(UUID delivererActorId, UUID tenantId,
                                                    UUID agencyId, UUID branchId, UUID missionId) {
        return new DelivererMissionAssignedEvent(UUID.randomUUID(), delivererActorId, tenantId,
                agencyId, branchId, missionId, Instant.now());
    }
}
