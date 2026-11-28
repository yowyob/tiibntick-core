package com.yowyob.tiibntick.common.kafka;

/**
 * Classpath locations of the JSON Schemas (draft 2020-12) of the aggregate events — spec §3.6, decision
 * [G.6]. Tests validate every produced record against them (with {@code com.networknt:json-schema-validator},
 * a test dependency); the Kafka guide (lot L9) includes the files as they are.
 *
 * <p>Fields may be added ({@code additionalProperties: true}); a breaking change goes to a new
 * {@code v<schemaVersion>} directory.
 */
public final class TntEventSchemas {

    /** Directory of schema version 1. */
    public static final String V1 = "kafka/schemas/v1/";
    /** Common envelope ({@link TntAggregateEvent}) with {@code $defs/maskedAddress}. */
    public static final String ENVELOPE = V1 + "envelope.schema.json";

    private TntEventSchemas() {
    }

    /**
     * @return location of the payload schema of {@code aggregateType} (created by the lot owning the aggregate:
     *         L2 parcel/delivery/announcement, L3 incident, L4 dispute)
     */
    public static String payload(final TntAggregateType aggregateType) {
        return V1 + aggregateType.name().toLowerCase(java.util.Locale.ROOT) + ".payload.schema.json";
    }

    /** @return location of the {@code details} schema of {@code eventType} (only for non-empty details) */
    public static String details(final String eventType) {
        return V1 + "details/" + eventType + ".schema.json";
    }
}
