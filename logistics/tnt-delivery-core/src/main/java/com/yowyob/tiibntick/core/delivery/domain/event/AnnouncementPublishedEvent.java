package com.yowyob.tiibntick.core.delivery.domain.event;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a client publishes a delivery announcement.
 * Triggers broadcast to eligible delivery persons in the zone.
 *
 * @author MANFOUO Braun
 */
public record AnnouncementPublishedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID clientId,
        DeliveryAddress pickupAddress,
        DeliveryAddress deliveryAddress,
        DeliveryUrgency urgency,
        BigDecimal offeredAmount,
        String currency,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public AnnouncementPublishedEvent(UUID announcementId, UUID tenantId, UUID clientId,
                                       DeliveryAddress pickup, DeliveryAddress delivery,
                                       DeliveryUrgency urgency, BigDecimal offeredAmount,
                                       String currency, Instant occurredAt) {
        this(UUID.randomUUID(), announcementId, tenantId, clientId,
                pickup, delivery, urgency, offeredAmount, currency, occurredAt);
    }
}
