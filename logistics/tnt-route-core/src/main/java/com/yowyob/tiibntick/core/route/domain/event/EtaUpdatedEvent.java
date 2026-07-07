package com.yowyob.tiibntick.core.route.domain.event;

import java.time.Instant;
import java.util.UUID;

public record EtaUpdatedEvent(UUID eventId, UUID tenantId, String missionId,
                               Instant newEtaExpected, Instant newEtaMin, Instant newEtaMax,
                               double confidence, Instant occurredAt) {
    public static EtaUpdatedEvent of(UUID tenantId, String missionId,
                                      Instant expected, Instant min, Instant max, double confidence) {
        return new EtaUpdatedEvent(UUID.randomUUID(), tenantId, missionId,
                expected, min, max, confidence, Instant.now());
    }
}
