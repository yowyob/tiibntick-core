package com.yowyob.tiibntick.core.agency.eventing.domain.event;

import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event: a contract was signed.
 * Routed to {@code tnt.agency.contract.events}.
 * {@code contractType} carries the enum name (e.g. FREELANCER_AGREEMENT) to keep this module
 * free of cross-module enum dependencies while preserving the wire format.
 */
public record ContractSigned(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID agencyId,
        UUID delivererId,
        String contractType,
        Instant occurredAt
) implements TntDomainEvent {

    @Override public String getEventType()     { return "ContractSigned"; }
    @Override public UUID getEventId()         { return eventId; }
    @Override public UUID getAggregateId()     { return aggregateId; }
    @Override public String getAggregateType() { return "Contract"; }
    @Override public UUID getTenantId()        { return tenantId; }
    @Override public Instant getOccurredAt()   { return occurredAt; }
    @Override public String getCorrelationId() { return eventId.toString(); }
    @Override public long getSequenceNumber()  { return 1L; }
}
