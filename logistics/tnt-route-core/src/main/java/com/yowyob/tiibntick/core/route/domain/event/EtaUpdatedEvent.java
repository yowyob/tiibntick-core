package com.yowyob.tiibntick.core.route.domain.event;

import java.time.Instant;
import java.util.UUID;

public record EtaUpdatedEvent(UUID eventId, UUID tenantId, String missionId, String trackingCode,
                               Instant newEtaExpected, Instant newEtaMin, Instant newEtaMax,
                               double confidence, double remainingDistanceKm, Instant occurredAt) {
    public static EtaUpdatedEvent of(UUID tenantId, String missionId, String trackingCode,
                                      Instant expected, Instant min, Instant max, double confidence,
                                      double remainingDistanceKm) {
        return new EtaUpdatedEvent(UUID.randomUUID(), tenantId, missionId, trackingCode,
                expected, min, max, confidence, remainingDistanceKm, Instant.now());
    }
}
