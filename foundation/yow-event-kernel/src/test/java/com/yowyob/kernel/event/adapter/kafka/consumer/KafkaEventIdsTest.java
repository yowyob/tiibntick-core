package com.yowyob.kernel.event.adapter.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaEventIds — stable identity of a consumed record")
class KafkaEventIdsTest {

    static ConsumerRecord<Object, Object> record(Object value, long offset, String... headers) {
        return record(value, offset, 1_000L, headers);
    }

    static ConsumerRecord<Object, Object> record(Object value, long offset, long timestamp, String... headers) {
        RecordHeaders h = new RecordHeaders();
        for (int i = 0; i < headers.length; i += 2) {
            h.add(headers[i], headers[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return new ConsumerRecord<>("tnt.topic", 1, offset, timestamp, TimestampType.CREATE_TIME, 0, 0, "k", value, h,
                Optional.empty());
    }

    @Test
    @DisplayName("the outbox envelope id header wins over everything else")
    void envelopeHeaderFirst() {
        var r = record("{\"eventId\":\"payload-id\"}", 7, "X-Yow-Event-Id", "event-header", "X-Yow-Envelope-Id", "env-1");
        assertThat(KafkaEventIds.resolve(r)).isEqualTo("env-1");
    }

    @Test
    @DisplayName("X-Yow-Event-Id then eventId headers come next")
    void eventIdHeaders() {
        assertThat(KafkaEventIds.resolve(record("{}", 1, "X-Yow-Event-Id", "h-1"))).isEqualTo("h-1");
        assertThat(KafkaEventIds.resolve(record("{}", 1, "eventId", "h-2"))).isEqualTo("h-2");
    }

    @Test
    @DisplayName("the top-level eventId of a JSON payload (String or byte[]) is used, nested ones are ignored")
    void payloadEventId() {
        assertThat(KafkaEventIds.resolve(record("{\"payload\":{\"eventId\":\"nested\"},\"eventId\":\"top\"}", 1)))
                .isEqualTo("top");
        assertThat(KafkaEventIds.resolve(record("{\"eventId\":\"bytes\"}".getBytes(StandardCharsets.UTF_8), 1)))
                .isEqualTo("bytes");
        assertThat(KafkaEventIds.resolve(record("{\"payload\":{\"eventId\":\"nested\"}}", 3)))
                .startsWith("tpo:");
    }

    @Test
    @DisplayName("without any id the fallback hashes topic+partition+offset+timestamp: stable per record, "
            + "distinct for another offset or a recreated topic")
    void fallbackIsStablePerRecord() {
        String a = KafkaEventIds.resolve(record("not json", 42));
        assertThat(a).isEqualTo(KafkaEventIds.resolve(record("other value", 42))).startsWith("tpo:");
        assertThat(KafkaEventIds.resolve(record("not json", 43))).isNotEqualTo(a);
        assertThat(KafkaEventIds.resolve(record("not json", 42, 2_000L))).as("recreated topic, same offset")
                .isNotEqualTo(a);
        assertThat(KafkaEventIds.resolve(record(null, 44))).startsWith("tpo:");
        assertThat(KafkaEventIds.resolve(record("{\"eventId\":null}", 45))).startsWith("tpo:");
    }

    @Test
    @DisplayName("over-long ids are hashed to fit the key column")
    void longIdsAreHashed() {
        String id = KafkaEventIds.resolve(record("{}", 1, "X-Yow-Event-Id", "x".repeat(300)));
        assertThat(id).startsWith("sha:").hasSizeLessThanOrEqualTo(KafkaEventIds.MAX_ID_LENGTH);
    }

    @Test
    @DisplayName("X-Yow-Replay: true marks an explicit replay")
    void replayHeader() {
        assertThat(KafkaEventIds.isReplay(record("{}", 1, "X-Yow-Replay", "true"))).isTrue();
        assertThat(KafkaEventIds.isReplay(record("{}", 1))).isFalse();
    }
}
