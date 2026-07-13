package com.yowyob.tiibntick.core.agency.eventing.domain.event;

import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event: a deliverer's availability/status changed.
 * Routed to {@code tnt.agency.events}.
 * {@code status} carries the enum name (AVAILABLE, ON_MISSION, OFFLINE, SUSPENDED, INACTIVE).
 */
public record DelivererAvailabilityChanged(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID agencyId,
        String status,
        Instant changedAt,
        Instant occurredAt
) implements TntDomainEvent {

    @Override public String getEventType()     { return "DelivererAvailabilityChanged"; }
    @Override public UUID getEventId()         { return eventId; }
    @Override public UUID getAggregateId()     { return aggregateId; }
    @Override public String getAggregateType() { return "Deliverer"; }
    @Override public UUID getTenantId()        { return tenantId; }
    @Override public Instant getOccurredAt()   { return occurredAt; }
    @Override public String getCorrelationId() { return eventId.toString(); }
    @Override public long getSequenceNumber()  { return 1L; }
}
