package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

public record BadgeEarnedEvent(
        UUID eventId,
        UUID actorId,
        UUID tenantId,
        String actorType,
        String badgeCode,
        String badgeLabel,
        Instant occurredAt) {

    public static BadgeEarnedEvent of(UUID actorId, UUID tenantId, String actorType,
                                       String badgeCode, String badgeLabel) {
        return new BadgeEarnedEvent(UUID.randomUUID(), actorId, tenantId, actorType,
                badgeCode, badgeLabel, Instant.now());
    }
}
