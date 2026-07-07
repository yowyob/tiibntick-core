package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ActorStatusChangedEvent(
        UUID eventId,
        UUID actorId,
        UUID tenantId,
        String oldStatus,
        String newStatus,
        String reason,
        Instant occurredAt) {

    public static ActorStatusChangedEvent of(UUID actorId, UUID tenantId,
                                              String oldStatus, String newStatus, String reason) {
        return new ActorStatusChangedEvent(UUID.randomUUID(), actorId, tenantId,
                oldStatus, newStatus, reason, Instant.now());
    }
}
