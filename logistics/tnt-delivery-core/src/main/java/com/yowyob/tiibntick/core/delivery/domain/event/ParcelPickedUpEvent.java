package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when the delivery person confirms physical pickup of the parcel.
 *
 * @author MANFOUO Braun
 */
public record ParcelPickedUpEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID deliveryPersonId,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public ParcelPickedUpEvent(UUID deliveryId, UUID tenantId,
                                UUID deliveryPersonId, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, deliveryPersonId, occurredAt);
    }
}
