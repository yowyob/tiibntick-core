package com.yowyob.tiibntick.core.agency.eventing.domain.event;

import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event: a parcel arrived at an agency relay hub.
 * Routed to {@code tnt.agency.events}.
 */
public record ParcelAtAgencyHub(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID hubId,
        UUID packageId,
        String trackingCode,
        Instant withdrawalDeadline,
        Instant occurredAt
) implements TntDomainEvent {

    @Override public String getEventType()     { return "ParcelAtAgencyHub"; }
    @Override public UUID getEventId()         { return eventId; }
    @Override public UUID getAggregateId()     { return aggregateId; }
    @Override public String getAggregateType() { return "HubParcelRecord"; }
    @Override public UUID getTenantId()        { return tenantId; }
    @Override public Instant getOccurredAt()   { return occurredAt; }
    @Override public String getCorrelationId() { return eventId.toString(); }
    @Override public long getSequenceNumber()  { return 1L; }
}
