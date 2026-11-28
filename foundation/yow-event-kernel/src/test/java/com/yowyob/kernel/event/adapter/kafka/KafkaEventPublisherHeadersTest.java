package com.yowyob.kernel.event.adapter.kafka;

import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KafkaEventPublisher: key = aggregate id, X-Yow-Event-Id = event id = envelope id (lot L0)")
class KafkaEventPublisherHeadersTest {

    @Test
    @SuppressWarnings("unchecked")
    void eventIdHeaderIsTheEnvelopeIdAndKeyIsTheAggregateId() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        KafkaEventPublisher publisher = new KafkaEventPublisher(template);

        String eventId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();
        DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
            .eventId(eventId)
            .correlationId("req-1").eventType("DeliveryPickedUp").aggregateId(aggregateId).aggregateType("DELIVERY")
            .tenantId("tenant").solutionCode("TNT").payload("{\"eventId\":\"" + eventId + "\"}")
            .schemaVersion(1).kafkaTopic("tnt.delivery.events").build();

        publisher.publish(envelope, Map.of()).block(Duration.ofSeconds(5));
        publisher.publishAsReplay(envelope).block(Duration.ofSeconds(5));

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template, org.mockito.Mockito.times(2)).send(sent.capture());
        for (ProducerRecord<String, String> record : sent.getAllValues()) {
            assertThat(record.topic()).isEqualTo("tnt.delivery.events");
            assertThat(record.key()).isEqualTo(aggregateId);
            assertThat(header(record, "X-Yow-Event-Id")).isEqualTo(eventId);
            assertThat(header(record, "X-Yow-Envelope-Id")).isEqualTo(eventId);
            assertThat(header(record, "X-Yow-Event-Type")).isEqualTo("DeliveryPickedUp");
            assertThat(header(record, "X-Yow-Aggregate-Type")).isEqualTo("DELIVERY");
            assertThat(header(record, "X-Yow-Schema-Version")).isEqualTo("1");
        }
        assertThat(header(sent.getAllValues().get(1), "X-Yow-Replay")).isEqualTo("true");
        assertThat(envelope.getEventId()).isEqualTo(envelope.getId().value());
    }

    @Test
    void anEnvelopeWithoutExplicitEventIdStillCarriesOne() {
        DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
            .correlationId("c").eventType("T").aggregateId("a").aggregateType("A")
            .tenantId("t").solutionCode("TNT").payload("{}").kafkaTopic("topic").build();
        assertThat(envelope.getEventId()).isNotBlank().isEqualTo(envelope.getId().value());
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
