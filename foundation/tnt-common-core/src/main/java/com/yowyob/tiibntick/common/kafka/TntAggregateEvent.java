package com.yowyob.tiibntick.common.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Common envelope of every record published on an aggregate topic ({@link TntAggregateType#topic()}) —
 * decisions [G.1][G.2][G.6] of 2026-11-26, {@code cible-evenements-colis-livraison.md} §3.3, schema
 * {@code kafka/schemas/v1/envelope.schema.json} ({@link TntEventSchemas#ENVELOPE}).
 *
 * <p>Serialised by Jackson with null fields <b>present</b>, {@code occurredAt} as ISO-8601 UTC (use
 * {@link TntEventJson#mapper()} or any mapper with the JSR-310 module and
 * {@code WRITE_DATES_AS_TIMESTAMPS} disabled), amounts as decimal strings ({@link TntEventMasking#amount}).
 *
 * <p>Outbox mapping (yow-event-kernel {@code DomainEventEnvelope}), so that body and headers agree:
 * envelope id = {@link #eventId()} (header {@code X-Yow-Event-Id} and {@code X-Yow-Envelope-Id}),
 * {@code eventType}, {@code aggregateId} (also the partition key — never pass an explicit key),
 * {@code aggregateType} = {@code aggregateType().name()}, {@code tenantId}, {@code schemaVersion},
 * {@code correlationId}, {@code causationId}, {@code kafkaTopic} = {@code aggregateType().topic()}.
 *
 * @param eventId          unique id of the event (= outbox envelope id = header {@code X-Yow-Event-Id})
 * @param eventType        {@code <Aggregate><PascalCase>} ({@link TntAggregateType#eventTypeFor})
 * @param schemaVersion    starts at 1; a breaking change increments it [G.6]
 * @param occurredAt       when the change happened (UTC)
 * @param aggregateType    one of the five aggregates
 * @param aggregateId      aggregate id (UUID text) — also the Kafka key
 * @param aggregateVersion optimistic version of the aggregate <b>after</b> the change; consumers projecting a
 *                         state ignore an event whose version is ≤ the last one applied
 * @param tenantId         tenant of the change (UUID text), may be {@code null}
 * @param correlationId    request correlation id, may be {@code null}
 * @param causationId      {@code eventId} of the event that caused this one, may be {@code null}
 * @param trigger          use-case or cause code, UPPER_SNAKE_CASE (spec §1 "Déclencheur" columns)
 * @param actor            who caused the change (type + pseudonymous id, never a name)
 * @param previousState    {@code {"status": <old status>, <old value of each changed payload field>}};
 *                         {@code null} for a creation event
 * @param payload          complete, masked snapshot of the aggregate after the change [G.2][G.3]
 * @param details          event-specific data absent from the snapshot; empty object otherwise
 * @param <S>              snapshot type of the aggregate
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"eventId", "eventType", "schemaVersion", "occurredAt", "aggregateType", "aggregateId",
    "aggregateVersion", "tenantId", "correlationId", "causationId", "trigger", "actor", "previousState",
    "payload", "details"})
public record TntAggregateEvent<S>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        TntAggregateType aggregateType,
        String aggregateId,
        long aggregateVersion,
        String tenantId,
        String correlationId,
        String causationId,
        String trigger,
        TntEventActor actor,
        Map<String, Object> previousState,
        S payload,
        Object details) {

    /** Current envelope schema version. */
    public static final int SCHEMA_VERSION_1 = 1;

    /** Same pattern as {@code envelope.schema.json} {@code eventType}. */
    private static final Pattern EVENT_TYPE = Pattern.compile("^[A-Z][A-Za-z]+$");
    private static final Pattern TRIGGER = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    public TntAggregateEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(payload, "payload");
        if (!EVENT_TYPE.matcher(eventType).matches() || !eventType.startsWith(aggregateType.eventTypePrefix())) {
            throw new IllegalArgumentException("eventType '" + eventType + "' must match " + EVENT_TYPE
                + " and start with '" + aggregateType.eventTypePrefix() + "'");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must be >= 0");
        }
        requireUuid(aggregateId, "aggregateId");
        if (tenantId != null) {
            requireUuid(tenantId, "tenantId");
        }
        if (causationId != null) {
            requireUuid(causationId, "causationId");
        }
        if (!TRIGGER.matcher(trigger).matches()) {
            throw new IllegalArgumentException("trigger '" + trigger + "' must be UPPER_SNAKE_CASE");
        }
        previousState = previousState == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(previousState));
        details = details == null ? Map.of() : details;
    }

    /** @return the topic this event is published on */
    public String topic() {
        return aggregateType.topic();
    }

    /**
     * Builds {@code previousState}: the old status first, then the old value of each payload field the
     * change modified (minimal diff, spec §3.3).
     *
     * @param previousStatus old status (for a change without transition: the current status)
     * @param changedFields  old values of the modified payload fields, may be {@code null}
     * @return an ordered, unmodifiable map
     */
    public static Map<String, Object> previousState(final String previousStatus, final Map<String, ?> changedFields) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", previousStatus);
        if (changedFields != null) {
            changedFields.forEach((field, value) -> {
                if (!"status".equals(field)) {
                    state.put(field, value);
                }
            });
        }
        return Collections.unmodifiableMap(state);
    }

    /**
     * Tolerant check used by consumers of a topic that still carries legacy records (spec §3.9): a record
     * without {@code schemaVersion}/{@code eventId}/{@code aggregateType} is not an aggregate event and must
     * be ignored (DEBUG log), not failed.
     *
     * @param body parsed record value
     * @return {@code true} if the body looks like a {@link TntAggregateEvent}
     */
    public static boolean isAggregateEvent(final JsonNode body) {
        return body != null && body.isObject()
            && body.path("schemaVersion").isIntegralNumber()
            && body.path("eventId").isTextual()
            && body.path("aggregateType").isTextual();
    }

    /** @return a builder with a random {@code eventId}, {@code occurredAt = now} (ms) and schema version 1 */
    public static <S> Builder<S> builder(final TntAggregateType aggregateType) {
        return new Builder<>(aggregateType);
    }

    private static void requireUuid(final String value, final String field) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a UUID: " + value, e);
        }
    }

    /** Fluent builder; {@link #build()} applies the record's validation. */
    public static final class Builder<S> {

        private final TntAggregateType aggregateType;
        private UUID eventId = UUID.randomUUID();
        private String eventType;
        private int schemaVersion = SCHEMA_VERSION_1;
        private Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        private String aggregateId;
        private long aggregateVersion;
        private String tenantId;
        private String correlationId;
        private String causationId;
        private String trigger;
        private TntEventActor actor;
        private Map<String, Object> previousState;
        private S payload;
        private Object details;

        private Builder(final TntAggregateType aggregateType) {
            this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        }

        public Builder<S> eventId(final UUID value) { this.eventId = value; return this; }
        public Builder<S> eventType(final String value) { this.eventType = value; return this; }
        /** Sets {@code eventType} from a target state or change kind (mechanical rule, spec §1.0). */
        public Builder<S> eventTypeFor(final String stateOrChangeKind) {
            this.eventType = aggregateType.eventTypeFor(stateOrChangeKind);
            return this;
        }
        public Builder<S> schemaVersion(final int value) { this.schemaVersion = value; return this; }
        public Builder<S> occurredAt(final Instant value) { this.occurredAt = value; return this; }
        public Builder<S> aggregateId(final String value) { this.aggregateId = value; return this; }
        public Builder<S> aggregateId(final UUID value) { this.aggregateId = value == null ? null : value.toString(); return this; }
        public Builder<S> aggregateVersion(final long value) { this.aggregateVersion = value; return this; }
        public Builder<S> tenantId(final String value) { this.tenantId = value; return this; }
        public Builder<S> tenantId(final UUID value) { this.tenantId = value == null ? null : value.toString(); return this; }
        public Builder<S> correlationId(final String value) { this.correlationId = value; return this; }
        public Builder<S> causationId(final String value) { this.causationId = value; return this; }
        public Builder<S> causationId(final UUID value) { this.causationId = value == null ? null : value.toString(); return this; }
        public Builder<S> trigger(final String value) { this.trigger = value; return this; }
        public Builder<S> actor(final TntEventActor value) { this.actor = value; return this; }
        public Builder<S> previousState(final Map<String, Object> value) { this.previousState = value; return this; }
        public Builder<S> payload(final S value) { this.payload = value; return this; }
        public Builder<S> details(final Object value) { this.details = value; return this; }

        public TntAggregateEvent<S> build() {
            return new TntAggregateEvent<>(eventId, eventType, schemaVersion, occurredAt, aggregateType, aggregateId,
                aggregateVersion, tenantId, correlationId, causationId, trigger, actor, previousState, payload,
                details);
        }
    }
}
