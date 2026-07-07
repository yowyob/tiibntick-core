package com.yowyob.tiibntick.core.route.domain.event;

import java.time.Instant;
import java.util.UUID;

public record TourOptimizedEvent(UUID eventId, UUID tenantId, String delivererId,
                                  int stopCount, double totalCostKm, Instant occurredAt) {
    public static TourOptimizedEvent of(UUID tenantId, String delivererId, int stops, double cost) {
        return new TourOptimizedEvent(UUID.randomUUID(), tenantId, delivererId, stops, cost, Instant.now());
    }
}
