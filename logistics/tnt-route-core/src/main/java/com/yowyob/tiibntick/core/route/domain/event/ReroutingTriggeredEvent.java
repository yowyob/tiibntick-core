package com.yowyob.tiibntick.core.route.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ReroutingTriggeredEvent(UUID eventId, UUID tenantId, String missionId,
                                       double oldCost, double newCost, String reason,
                                       Instant occurredAt) {
    public static ReroutingTriggeredEvent of(UUID tenantId, String missionId,
                                              double oldCost, double newCost, String reason) {
        return new ReroutingTriggeredEvent(UUID.randomUUID(), tenantId, missionId,
                oldCost, newCost, reason, Instant.now());
    }
}
