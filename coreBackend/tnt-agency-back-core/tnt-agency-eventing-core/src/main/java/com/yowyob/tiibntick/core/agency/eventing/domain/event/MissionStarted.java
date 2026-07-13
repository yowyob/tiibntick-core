package com.yowyob.tiibntick.core.agency.eventing.domain.event;

import com.yowyob.tiibntick.common.domain.event.TntDomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event: a mission was started (picked up).
 * Routed to {@code tnt.agency.mission.request}.
 */
public record MissionStarted(
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        UUID agencyId,
        UUID coreMissionId,
        UUID delivererId,
        Instant startedAt,
        Instant occurredAt
) implements TntDomainEvent {

    @Override public String getEventType()     { return "MissionStarted"; }
    @Override public UUID getEventId()         { return eventId; }
    @Override public UUID getAggregateId()     { return aggregateId; }
    @Override public String getAggregateType() { return "AgencyMission"; }
    @Override public UUID getTenantId()        { return tenantId; }
    @Override public Instant getOccurredAt()   { return occurredAt; }
    @Override public String getCorrelationId() { return eventId.toString(); }
    @Override public long getSequenceNumber()  { return 1L; }
}
