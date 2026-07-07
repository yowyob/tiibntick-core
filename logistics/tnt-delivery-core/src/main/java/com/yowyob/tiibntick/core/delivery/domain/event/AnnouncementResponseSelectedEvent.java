package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a client selects a delivery person's response, triggering delivery creation.
 *
 * @author MANFOUO Braun
 */
public record AnnouncementResponseSelectedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID clientId,
        UUID selectedDeliveryPersonId,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public AnnouncementResponseSelectedEvent(UUID announcementId, UUID tenantId,
                                              UUID clientId, UUID deliveryPersonId,
                                              Instant occurredAt) {
        this(UUID.randomUUID(), announcementId, tenantId, clientId, deliveryPersonId, occurredAt);
    }
}
