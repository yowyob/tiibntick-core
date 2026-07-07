package com.yowyob.tiibntick.core.route.domain.event;

import java.time.Instant;
import java.util.UUID;

public record VrpFallbackActivatedEvent(UUID eventId, UUID tenantId, String reason,
                                         int missionCount, Instant occurredAt) {
    public static VrpFallbackActivatedEvent of(UUID tenantId, String reason, int count) {
        return new VrpFallbackActivatedEvent(UUID.randomUUID(), tenantId, reason, count, Instant.now());
    }
}
