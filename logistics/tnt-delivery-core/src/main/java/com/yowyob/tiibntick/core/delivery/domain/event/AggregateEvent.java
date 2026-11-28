package com.yowyob.tiibntick.core.delivery.domain.event;

import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventActor;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One change of a parcel, delivery or announcement — the only event an aggregate of delivery-core records for a
 * transition (spec §1.0 rules 2–5, decision [C bis]: exactly one event per transition, mechanically named
 * {@code <Aggregate><PascalCase(target)>}; changes without transition: {@code <Aggregate><PascalCase(changeKind)>}).
 *
 * <p>The publisher turns it into a {@code TntAggregateEvent} on the aggregate topic, masking the snapshot (§3.4).
 * {@link #snapshot()} is the <b>unmasked</b> state of the aggregate after the change: it never leaves the process as is.
 *
 * @param aggregateType    PARCEL, DELIVERY or ANNOUNCEMENT
 * @param eventId          unique id (= outbox envelope id)
 * @param aggregateId      aggregate id (Kafka key)
 * @param tenantId         tenant of the change, may be {@code null} (a parcel has no tenant of its own)
 * @param occurredAt       when the change happened
 * @param aggregateVersion optimistic version of the aggregate after the change (the version written by this save)
 * @param eventType        mechanical type
 * @param row              transition row ({@code D7}, {@code P11}, {@code A4}), {@code null} for a change without
 *                         transition
 * @param previousStatus   state before the change ({@code null} for a creation)
 * @param previousFields   old value of each snapshot field changed besides the status (minimal diff)
 * @param trigger          use case or cause (UPPER_SNAKE_CASE)
 * @param actor            who caused it
 * @param causationId      event that caused this one (a delivery event for a parcel event), may be {@code null}
 * @param details          event-specific data absent from the snapshot
 * @param snapshot         state after the change (DeliverySnapshot, ParcelSnapshot or AnnouncementSnapshot)
 */
public record AggregateEvent(
        TntAggregateType aggregateType,
        UUID eventId,
        UUID aggregateId,
        UUID tenantId,
        Instant occurredAt,
        long aggregateVersion,
        String eventType,
        String row,
        String previousStatus,
        Map<String, Object> previousFields,
        String trigger,
        TntEventActor actor,
        UUID causationId,
        Map<String, Object> details,
        Object snapshot) implements DeliveryDomainEvent {

    public AggregateEvent {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(snapshot, "snapshot");
        // null values are meaningful (an old value that was empty): no Map.copyOf here
        previousFields = previousFields == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(previousFields));
        details = details == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(details));
    }

    /** @return whether this event records a transition (as opposed to a change without transition) */
    public boolean isTransition() {
        return row != null;
    }

    /** Version that follows a persisted version ({@code null} = not persisted yet: 0). */
    public static long nextVersion(Long persistedVersion) {
        return persistedVersion == null ? 0L : persistedVersion + 1;
    }
}
