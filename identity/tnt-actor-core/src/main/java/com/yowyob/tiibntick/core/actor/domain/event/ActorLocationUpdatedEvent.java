package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ActorLocationUpdatedEvent(
        UUID eventId,
        UUID actorId,
        UUID tenantId,
        String actorType,
        double latitude,
        double longitude,
        Double accuracy,
        String source,
        Instant occurredAt) {

    public static ActorLocationUpdatedEvent of(UUID actorId, UUID tenantId, String actorType,
                                                double latitude, double longitude,
                                                Double accuracy, String source) {
        return new ActorLocationUpdatedEvent(UUID.randomUUID(), actorId, tenantId, actorType,
                latitude, longitude, accuracy, source, Instant.now());
    }
}
