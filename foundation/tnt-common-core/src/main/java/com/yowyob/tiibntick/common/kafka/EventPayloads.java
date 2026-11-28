package com.yowyob.tiibntick.common.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tolerant reader for Kafka event payloads.
 *
 * <p>Two JSON shapes travel on the platform's topics:
 * <ul>
 *   <li><b>flat</b> — the event object itself ({@code {"missionId": "...", "newStatus": "..."}});</li>
 *   <li><b>enveloped</b> — {@code {"eventType": "...", "aggregateId": "...", "tenantId": "...",
 *       "occurredAt": "...", "payload": { ...event fields... }}}, which is what
 *       {@code KafkaDeliveryEventPublisher} puts on every {@code tnt.delivery.*} topic.</li>
 * </ul>
 * Consumers that read business fields at the root of the document (almost all of them) silently
 * saw nothing on enveloped topics. {@link #unwrap(JsonNode)} returns the event fields in both
 * cases, so a consumer works with either shape and a producer can evolve without a flag day.
 *
 * <p>When unwrapping, the envelope's {@code tenantId}, {@code aggregateId} and {@code occurredAt}
 * are copied onto the event object if the event does not already carry a field of that name.
 */
public final class EventPayloads {

    private static final String[] ENVELOPE_FIELDS = {"tenantId", "aggregateId", "occurredAt"};

    private EventPayloads() {
    }

    /** Parses {@code json} and unwraps it; see {@link #unwrap(JsonNode)}. */
    public static JsonNode parse(ObjectMapper mapper, String json) throws JsonProcessingException {
        return unwrap(mapper.readTree(json));
    }

    /**
     * @return the event fields: {@code root.payload} (enriched with the envelope's tenant /
     *         aggregate / timestamp) when {@code root} is an envelope, {@code root} itself otherwise
     */
    public static JsonNode unwrap(JsonNode root) {
        if (root == null || !root.isObject()) {
            return root;
        }
        JsonNode inner = root.get("payload");
        if (!root.hasNonNull("eventType") || inner == null || !inner.isObject()) {
            return root;
        }
        ObjectNode merged = ((ObjectNode) inner).deepCopy();
        for (String field : ENVELOPE_FIELDS) {
            if (!merged.hasNonNull(field) && root.hasNonNull(field)) {
                merged.set(field, root.get(field));
            }
        }
        return merged;
    }
}
