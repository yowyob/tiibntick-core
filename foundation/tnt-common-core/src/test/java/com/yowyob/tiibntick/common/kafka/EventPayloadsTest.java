package com.yowyob.tiibntick.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventPayloadsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void flatEventIsReturnedUnchanged() throws Exception {
        JsonNode node = EventPayloads.parse(mapper, "{\"missionId\":\"m1\",\"newStatus\":\"DELIVERED\"}");
        assertThat(node.get("missionId").asText()).isEqualTo("m1");
        assertThat(node.get("newStatus").asText()).isEqualTo("DELIVERED");
    }

    @Test
    void envelopedEventIsUnwrappedAndEnrichedWithEnvelopeFields() throws Exception {
        JsonNode node = EventPayloads.parse(mapper, "{\"eventType\":\"MissionStatusChangedEvent\","
                + "\"aggregateId\":\"agg\",\"tenantId\":\"t1\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"payload\":{\"missionId\":\"m1\",\"newStatus\":\"IN_TRANSIT\",\"tenantId\":\"t-inner\"}}");
        assertThat(node.get("missionId").asText()).isEqualTo("m1");
        assertThat(node.get("newStatus").asText()).isEqualTo("IN_TRANSIT");
        assertThat(node.get("tenantId").asText()).as("the event's own field wins").isEqualTo("t-inner");
        assertThat(node.get("aggregateId").asText()).isEqualTo("agg");
        assertThat(node.get("occurredAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(node.has("eventType")).isFalse();
    }

    @Test
    void aDocumentThatMerelyHasAPayloadFieldIsNotAnEnvelope() throws Exception {
        JsonNode node = EventPayloads.parse(mapper, "{\"payload\":{\"a\":1},\"id\":\"x\"}");
        assertThat(node.get("id").asText()).isEqualTo("x");
        assertThat(node.has("payload")).isTrue();
    }

    @Test
    void nonObjectDocumentsAreReturnedAsIs() throws Exception {
        assertThat(EventPayloads.parse(mapper, "[1,2]").isArray()).isTrue();
        assertThat(EventPayloads.unwrap(null)).isNull();
    }
}
