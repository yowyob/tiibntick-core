package com.yowyob.tiibntick.common.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TiiBnTick-specific metadata for domain events.
 *
 * <p>The Yowyob Kernel provides a base {@code IDomainEvent} interface with minimal fields.
 * TiiBnTick logistics events require additional tracking fields absent from the Kernel:
 * <ul>
 *   <li>{@code correlationId} — HTTP request chain propagation (X-Correlation-Id header)</li>
 *   <li>{@code sequenceNumber} — per-aggregate event ordering for optimistic concurrency</li>
 * </ul>
 *
 * <p>This record is used <strong>by composition</strong> inside concrete TNT event classes.
 * It does NOT replace or re-implement the Kernel's IDomainEvent — it extends it.
 *
 * <p>Usage in a concrete event:
 * <pre>{@code
 * public record MissionCreatedEvent(
 *     TntDomainEventMetadata metadata,   // ← embedded here
 *     UUID missionId,
 *     String reference
 * ) {
 *     // Delegate Kernel IDomainEvent fields to metadata
 *     public UUID eventId()        { return metadata.eventId(); }
 *     public String eventType()    { return "MissionCreated"; }
 *     public UUID aggregateId()    { return missionId; }
 *     public UUID tenantId()       { return metadata.tenantId(); }
 *     public Instant occurredAt()  { return metadata.occurredAt(); }
 *     // TiiBnTick-specific extras
 *     public String correlationId(){ return metadata.correlationId(); }
 *     public long sequenceNumber() { return metadata.sequenceNumber(); }
 * }
 * }</pre>
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public record TntDomainEventMetadata(
    UUID eventId,
    UUID tenantId,
    UUID aggregateId,
    String aggregateType,
    Instant occurredAt,
    String correlationId,
    long sequenceNumber
) {

    // ─── Compact canonical constructor with validation ───────────────────

    public TntDomainEventMetadata {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be >= 0");
        }
        // correlationId may be null (system-initiated events with no HTTP context)
    }

    // ─── Factory methods ────────────────────────────────────────────────

    /**
     * Creates metadata with a new random eventId, current timestamp, and sequence 0.
     *
     * @param tenantId      the tenant owning this event
     * @param aggregateId   the primary key of the aggregate that emitted the event
     * @param aggregateType the DDD aggregate type name (e.g., "Mission", "Package")
     */
    public static TntDomainEventMetadata of(UUID tenantId, UUID aggregateId, String aggregateType) {
        return new TntDomainEventMetadata(
            UUID.randomUUID(),
            tenantId,
            aggregateId,
            aggregateType,
            Instant.now(),
            null,
            0L
        );
    }

    /**
     * Creates metadata with a specific correlation ID (e.g., from HTTP request header).
     */
    public static TntDomainEventMetadata of(UUID tenantId, UUID aggregateId,
                                             String aggregateType, String correlationId) {
        return new TntDomainEventMetadata(
            UUID.randomUUID(),
            tenantId,
            aggregateId,
            aggregateType,
            Instant.now(),
            correlationId,
            0L
        );
    }

    // ─── Wither methods — create derived instances ───────────────────────

    /**
     * Returns a new metadata with the given correlation ID set.
     * Useful when the correlation ID is resolved after initial creation.
     */
    public TntDomainEventMetadata withCorrelationId(String correlationId) {
        return new TntDomainEventMetadata(
            eventId, tenantId, aggregateId, aggregateType, occurredAt, correlationId, sequenceNumber);
    }

    /**
     * Returns a new metadata with the given sequence number.
     * The sequence number should be the current aggregate version at the time of the event.
     */
    public TntDomainEventMetadata withSequenceNumber(long sequenceNumber) {
        return new TntDomainEventMetadata(
            eventId, tenantId, aggregateId, aggregateType, occurredAt, correlationId, sequenceNumber);
    }

    /**
     * Returns a new metadata with a fresh eventId and current timestamp.
     * Used when replaying or re-publishing an event.
     */
    public TntDomainEventMetadata refreshed() {
        return new TntDomainEventMetadata(
            UUID.randomUUID(), tenantId, aggregateId, aggregateType,
            Instant.now(), correlationId, sequenceNumber);
    }
}
