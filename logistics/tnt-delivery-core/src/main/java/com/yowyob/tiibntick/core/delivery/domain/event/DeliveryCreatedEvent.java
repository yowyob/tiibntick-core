package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a new delivery is created from an announcement response selection.
 *
 * @author MANFOUO Braun
 */
public record DeliveryCreatedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public DeliveryCreatedEvent(UUID deliveryId, UUID tenantId, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, occurredAt);
    }
}
