package com.yowyob.tiibntick.core.delivery.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for all delivery domain events.
 * Events are collected in aggregates and published by the application layer
 * after successful persistence (transactional outbox pattern).
 *
 * @author MANFOUO Braun
 */
public interface DeliveryDomainEvent {

    /** Unique event identifier. */
    UUID eventId();

    /** Aggregate (delivery or announcement) that raised this event. */
    UUID aggregateId();

    /** Tenant context. */
    UUID tenantId();

    /** Timestamp when the event occurred. */
    Instant occurredAt();
}
