package com.yowyob.tiibntick.core.geo.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event fired when a service zone polygon is created or updated.
 * Consumed by tnt-actor-core and tnt-delivery-core for zone-eligibility checks.
 *
 * Author: MANFOUO Braun
 */
public record ServiceZoneUpdatedEvent(
        UUID eventId,
        UUID tenantId,
        UUID zoneId,
        UUID agencyId,
        String zoneName,
        String changeType,
        Instant occurredAt
) {
    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String DEACTIVATED = "DEACTIVATED";

    public ServiceZoneUpdatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(changeType, "changeType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static ServiceZoneUpdatedEvent created(UUID tenantId, UUID zoneId,
                                                   UUID agencyId, String zoneName) {
        return new ServiceZoneUpdatedEvent(
                UUID.randomUUID(), tenantId, zoneId, agencyId, zoneName, CREATED, Instant.now());
    }

    public static ServiceZoneUpdatedEvent updated(UUID tenantId, UUID zoneId,
                                                   UUID agencyId, String zoneName) {
        return new ServiceZoneUpdatedEvent(
                UUID.randomUUID(), tenantId, zoneId, agencyId, zoneName, UPDATED, Instant.now());
    }
}
