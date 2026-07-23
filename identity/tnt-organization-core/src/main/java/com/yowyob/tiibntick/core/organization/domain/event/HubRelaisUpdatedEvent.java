package com.yowyob.tiibntick.core.organization.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event — emitted whenever a {@code HubRelais} is mutated (capacity change,
 * operator reassignment, suspend/resume).
 *
 * @author MANFOUO Braun
 */
public record HubRelaisUpdatedEvent(
        UUID eventId,
        UUID hubId,
        UUID tenantId,
        String updateReason,
        Instant occurredAt) {

    public static HubRelaisUpdatedEvent of(UUID hubId, UUID tenantId, String updateReason) {
        return new HubRelaisUpdatedEvent(UUID.randomUUID(), hubId, tenantId, updateReason, Instant.now());
    }
}
