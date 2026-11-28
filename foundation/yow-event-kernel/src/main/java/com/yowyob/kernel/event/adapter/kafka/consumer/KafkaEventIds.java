package com.yowyob.kernel.event.adapter.kafka.consumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Resolves the stable identity of a consumed Kafka record, the key of the consumer-side
 * duplicate filter. In order of preference:
 * <ol>
 *   <li>header {@code X-Yow-Envelope-Id} — set by the yow-event-kernel outbox on every envelope;
 *       it is the envelope id, so a relay re-send carries the same value;</li>
 *   <li>header {@code X-Yow-Event-Id}, then header {@code eventId} — for producers that tag
 *       their records themselves;</li>
 *   <li>the top-level {@code "eventId"} field of a JSON payload — the domain events of most
 *       direct ({@code KafkaTemplate}) producers carry one;</li>
 *   <li>fallback {@code "tpo:" + SHA-256(topic|partition|offset|timestamp)} — identifies the record
 *       itself: catches a redelivery of the same record (consumer crash or rebalance before the
 *       offset commit), not a second production of the same event. The record timestamp is fixed
 *       once the record is in the log, so the id is stable across redeliveries; it keeps a topic
 *       that was deleted and recreated (offsets restarting at 0) from colliding with the old one.</li>
 * </ol>
 * Ids longer than {@link #MAX_ID_LENGTH} are stored as {@code "sha:" + SHA-256(id)}.
 */
public final class KafkaEventIds {

    public static final String HEADER_ENVELOPE_ID = "X-Yow-Envelope-Id";
    public static final String HEADER_EVENT_ID = "X-Yow-Event-Id";
    public static final String HEADER_EVENT_ID_PLAIN = "eventId";
    public static final String HEADER_REPLAY = "X-Yow-Replay";

    static final int MAX_ID_LENGTH = 128;
    private static final List<String> ID_HEADERS = List.of(HEADER_ENVELOPE_ID, HEADER_EVENT_ID, HEADER_EVENT_ID_PLAIN);
    private static final JsonFactory JSON = new JsonFactory();

    private KafkaEventIds() {
    }

    /** @return the record's event id (never null) */
    public static String resolve(final ConsumerRecord<?, ?> record) {
        for (String name : ID_HEADERS) {
            String value = header(record, name);
            if (usable(value)) {
                return bounded(value.trim());
            }
        }
        String fromPayload = payloadEventId(record.value());
        if (usable(fromPayload)) {
            return bounded(fromPayload.trim());
        }
        return "tpo:" + sha256(record.topic() + "|" + record.partition() + "|" + record.offset()
            + "|" + record.timestamp());
    }

    /** @return {@code true} when the record is an explicit replay ({@code X-Yow-Replay: true}) */
    public static boolean isReplay(final ConsumerRecord<?, ?> record) {
        return "true".equalsIgnoreCase(header(record, HEADER_REPLAY));
    }

    static String header(final ConsumerRecord<?, ?> record, final String name) {
        if (record.headers() == null) {
            return null;
        }
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Top-level "eventId" of a JSON object payload, read with a streaming parser (nested objects skipped). */
    static String payloadEventId(final Object value) {
        byte[] bytes;
        if (value instanceof String s) {
            bytes = s.getBytes(StandardCharsets.UTF_8);
        } else if (value instanceof byte[] b) {
            bytes = b;
        } else {
            return null;
        }
        try (JsonParser parser = JSON.createParser(bytes)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken token = parser.nextToken();
                if ("eventId".equals(field) && (token == JsonToken.VALUE_STRING || token == JsonToken.VALUE_NUMBER_INT)) {
                    return parser.getText();
                }
                parser.skipChildren();
            }
        } catch (Exception ignored) {
            // not JSON, or malformed: no payload id
        }
        return null;
    }

    private static boolean usable(final String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim());
    }

    private static String bounded(final String id) {
        return id.length() <= MAX_ID_LENGTH ? id : "sha:" + sha256(id);
    }

    static String sha256(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
