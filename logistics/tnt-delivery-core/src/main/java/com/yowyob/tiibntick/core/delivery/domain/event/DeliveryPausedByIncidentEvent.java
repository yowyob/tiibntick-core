package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a delivery is paused due to an active incident managed by tnt-incident-core.
 *
 * <p>Published when {@code MissionStatusPortAdapter.pauseMission()} is called.
 * tnt-incident-core listens to this event to confirm the delivery is blocked.
 *
 * @author MANFOUO Braun
 */
public record DeliveryPausedByIncidentEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID incidentId,
        String previousStatus,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public DeliveryPausedByIncidentEvent(UUID deliveryId, UUID tenantId,
                                          UUID incidentId, String previousStatus,
                                          Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, incidentId, previousStatus, occurredAt);
    }
}
