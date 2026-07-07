package com.yowyob.tiibntick.core.actor.domain.event;

import java.time.Instant;
import java.util.UUID;

public record KycValidatedEvent(
        UUID eventId,
        UUID actorId,
        UUID tenantId,
        String actorType,
        String kycStatus,
        String validatedBy,
        Instant occurredAt) {

    public static KycValidatedEvent of(UUID actorId, UUID tenantId, String actorType,
                                        String kycStatus, String validatedBy) {
        return new KycValidatedEvent(UUID.randomUUID(), actorId, tenantId, actorType,
                kycStatus, validatedBy, Instant.now());
    }
}
