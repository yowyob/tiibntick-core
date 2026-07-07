package com.yowyob.tiibntick.core.geo.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event fired when a new road node is created in the network.
 * Consumed by tnt-search to index the new location.
 *
 * Author: MANFOUO Braun
 */
public record RoadNodeCreatedEvent(
        UUID eventId,
        UUID tenantId,
        String nodeId,
        String nodeType,
        double latitude,
        double longitude,
        String name,
        String cityCode,
        Instant occurredAt
) {
    public RoadNodeCreatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(nodeType, "nodeType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static RoadNodeCreatedEvent of(UUID tenantId, String nodeId, String nodeType,
                                          double lat, double lng, String name, String cityCode) {
        return new RoadNodeCreatedEvent(
                UUID.randomUUID(), tenantId, nodeId, nodeType, lat, lng, name, cityCode, Instant.now());
    }
}
