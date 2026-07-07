package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a delivery fails due to an incident.
 *
 * @author MANFOUO Braun
 */
public record DeliveryFailedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        String reason,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public DeliveryFailedEvent(UUID deliveryId, UUID tenantId, String reason, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, reason, occurredAt);
    }
}
