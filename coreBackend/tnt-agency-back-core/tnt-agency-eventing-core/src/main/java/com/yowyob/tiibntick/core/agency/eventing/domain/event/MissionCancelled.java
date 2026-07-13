package com.yowyob.tiibntick.core.agency.eventing.domain.event;

import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event: a mission was cancelled.
 * Routed to {@code tnt.agency.mission.request}.
 */
public record MissionCancelled(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID agencyId,
        UUID coreMissionId,
        String reason,
        Instant cancelledAt,
        Instant occurredAt
) implements TntDomainEvent {

    @Override public String getEventType()     { return "MissionCancelled"; }
    @Override public UUID getEventId()         { return eventId; }
    @Override public UUID getAggregateId()     { return aggregateId; }
    @Override public String getAggregateType() { return "AgencyMission"; }
    @Override public UUID getTenantId()        { return tenantId; }
    @Override public Instant getOccurredAt()   { return occurredAt; }
    @Override public String getCorrelationId() { return eventId.toString(); }
    @Override public long getSequenceNumber()  { return 1L; }
}
