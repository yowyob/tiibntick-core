package com.yowyob.tiibntick.core.realtime.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Abstract base class for all domain events emitted by tnt-realtime-core.
 * Events are published via Kafka through {@code IRealtimeEventPublisher}.
 *
 * @author MANFOUO Braun
 */
public abstract class RealtimeDomainEvent {

    private final String eventId;
    private final String tenantId;
    private final LocalDateTime occurredAt;
    private final String solutionCode = "TNT";
    private final String sourceModule = "tnt-realtime-core";

    protected RealtimeDomainEvent(String tenantId) {
        this.eventId = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.occurredAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getSolutionCode() {
        return solutionCode;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    /**
     * Returns the Kafka topic this event should be published to.
     *
     * @return the Kafka topic name
     */
    public abstract String kafkaTopic();
}
