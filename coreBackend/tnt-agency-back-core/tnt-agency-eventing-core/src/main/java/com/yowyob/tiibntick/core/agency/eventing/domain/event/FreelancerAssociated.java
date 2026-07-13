package com.yowyob.tiibntick.core.agency.eventing.domain.event;

import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event: a freelancer was associated to an agency.
 * Routed to {@code tnt.agency.staff.events}.
 */
public record FreelancerAssociated(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID agencyId,
        UUID freelancerActorId,
        Instant occurredAt
) implements TntDomainEvent {

    @Override public String getEventType()     { return "FreelancerAssociated"; }
    @Override public UUID getEventId()         { return eventId; }
    @Override public UUID getAggregateId()     { return aggregateId; }
    @Override public String getAggregateType() { return "FreelancerAssociation"; }
    @Override public UUID getTenantId()        { return tenantId; }
    @Override public Instant getOccurredAt()   { return occurredAt; }
    @Override public String getCorrelationId() { return eventId.toString(); }
    @Override public long getSequenceNumber()  { return 1L; }
}
