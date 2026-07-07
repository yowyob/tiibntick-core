package com.yowyob.tiibntick.core.geo.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event fired when a road arc's traffic condition changes significantly,
 * triggering re-routing decisions in tnt-route-core.
 *
 * Author: MANFOUO Braun
 */
public record TrafficConditionChangedEvent(
        UUID eventId,
        UUID tenantId,
        String arcId,
        double previousTrafficFactor,
        double newTrafficFactor,
        Instant occurredAt
) {
    public TrafficConditionChangedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(arcId, "arcId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static TrafficConditionChangedEvent of(UUID tenantId, String arcId,
                                                   double previous, double current) {
        return new TrafficConditionChangedEvent(
                UUID.randomUUID(), tenantId, arcId, previous, current, Instant.now());
    }

    /**
     * Returns true if the congestion increase is significant enough to warrant re-routing.
     * Threshold: > 20% change in traffic factor.
     */
    public boolean isSignificant() {
        return Math.abs(newTrafficFactor - previousTrafficFactor) / previousTrafficFactor > 0.20;
    }
}
