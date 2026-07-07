package com.yowyob.tiibntick.core.delivery.domain.event;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a delivery enters or re-enters IN_TRANSIT status.
 *
 * @author MANFOUO Braun
 */
public record DeliveryInTransitEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID deliveryPersonId,
        EtaEstimate eta,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public DeliveryInTransitEvent(UUID deliveryId, UUID tenantId,
                                   UUID deliveryPersonId, EtaEstimate eta, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, deliveryPersonId, eta, occurredAt);
    }
}
