package com.yowyob.tiibntick.core.delivery.domain.event;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a delivery is successfully completed and handed to the recipient.
 *
 * @author MANFOUO Braun
 */
public record DeliveryCompletedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID deliveryPersonId,
        RecipientInfo recipient,
        DeliveryCost finalCost,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public DeliveryCompletedEvent(UUID deliveryId, UUID tenantId,
                                   UUID deliveryPersonId, RecipientInfo recipient,
                                   DeliveryCost finalCost, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, deliveryPersonId,
                recipient, finalCost, occurredAt);
    }
}
