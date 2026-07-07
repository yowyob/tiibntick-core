package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a delivery person is assigned to a delivery.
 *
 * @author MANFOUO Braun
 */
public record DeliveryPersonAssignedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID deliveryPersonId,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public DeliveryPersonAssignedEvent(UUID deliveryId, UUID tenantId,
                                        UUID deliveryPersonId, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, deliveryPersonId, occurredAt);
    }
}
