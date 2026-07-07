package com.yowyob.tiibntick.common.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Root contract for all TiiBnTick domain events.
 *
 * <p>Domain events capture the fact that something meaningful has happened within a bounded context.
 * They are immutable, named in past tense, and carry enough data for consumers to react without
 * querying back.
 *
 * <p>Each event must implement this interface. Concrete events are serialized via the Outbox pattern
 * (in {@code tnt-kernel-core}) and published to Kafka topics defined in {@code yow-event-kernel}.
 *
 * <p>Naming convention: {@code <Aggregate><Action>Event}
 * e.g., {@code MissionAssignedEvent}, {@code PackageDeliveredEvent}.
 *
 * Author: MANFOUO Braun
 */
public interface TntDomainEvent {

    /**
     * Unique identifier for this specific event occurrence.
     * Used for idempotency checks and deduplication in the event bus.
     */
    UUID getEventId();

    /**
     * Type of this event — typically the fully qualified class name or a stable string constant.
     * Used by consumers to route and deserialize the event.
     */
    String getEventType();

    /**
     * Identifier of the aggregate that produced this event.
     */
    UUID getAggregateId();

    /**
     * Logical type of the aggregate (e.g., "Mission", "Package", "Agency").
     */
    String getAggregateType();

    /**
     * Tenant in whose context this event occurred.
     */
    UUID getTenantId();

    /**
     * Moment this event occurred — wall-clock time in UTC.
     */
    Instant getOccurredAt();

    /**
     * Correlation ID linking this event to the originating HTTP/Kafka request chain.
     * Propagated via MDC / Reactor context.
     */
    String getCorrelationId();

    /**
     * Monotonically increasing sequence within the aggregate's lifetime.
     * Enables consumers to detect gaps and reorder events if needed.
     */
    long getSequenceNumber();
}
