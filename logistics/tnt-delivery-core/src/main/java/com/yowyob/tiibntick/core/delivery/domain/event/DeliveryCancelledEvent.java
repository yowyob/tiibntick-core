package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a delivery is cancelled before pickup.
 *
 * @author MANFOUO Braun
 */
public record DeliveryCancelledEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        String reason,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public DeliveryCancelledEvent(UUID deliveryId, UUID tenantId, String reason, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, reason, occurredAt);
    }
}
