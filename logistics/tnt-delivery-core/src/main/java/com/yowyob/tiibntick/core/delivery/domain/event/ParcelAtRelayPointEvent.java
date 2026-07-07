package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a parcel is deposited at a relay / hub point.
 *
 * @author MANFOUO Braun
 */
public record ParcelAtRelayPointEvent(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID relayPointId,
        Instant occurredAt
) implements DeliveryDomainEvent {

    public ParcelAtRelayPointEvent(UUID deliveryId, UUID tenantId,
                                    UUID relayPointId, Instant occurredAt) {
        this(UUID.randomUUID(), deliveryId, tenantId, relayPointId, occurredAt);
    }
}
