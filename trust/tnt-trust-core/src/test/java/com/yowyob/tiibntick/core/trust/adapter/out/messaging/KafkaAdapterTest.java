package com.yowyob.tiibntick.core.trust.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.test.StepVerifier;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Kafka adapters.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("tnt-trust — Kafka Adapter Tests")
class KafkaAdapterTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private KafkaTrustEventPublisherAdapter publisherAdapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        publisherAdapter = new KafkaTrustEventPublisherAdapter(
                kafkaTemplate, objectMapper, new SimpleMeterRegistry());
    }

    // ── KafkaTrustEventPublisherAdapter ────────────────────────────────────────

    @Nested
    @DisplayName("KafkaTrustEventPublisherAdapter")
    class PublisherAdapterTest {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("publish() should send to yow.trust.events with entityId as key")
        void shouldPublishToCorrectTopic() {
            final LogisticTrustEvent event = buildDeliveryProofEvent();

            // Mock the KafkaTemplate send to return a completed future
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            StepVerifier.create(publisherAdapter.publish(event))
                    .verifyComplete();

            // Verify topic and key
            final ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            final ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

            verify(kafkaTemplate).send(
                    topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("yow.trust.events");
            assertThat(keyCaptor.getValue()).isEqualTo(event.getEntityId());
            assertThat(valueCaptor.getValue()).contains("correlationId");
            assertThat(valueCaptor.getValue()).contains("TNT");
            assertThat(valueCaptor.getValue()).contains("DELIVERY_PROOF_RECORDED");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("publishAll() should send each event sequentially")
        void shouldPublishAllSequentially() {
            final LogisticTrustEvent e1 = buildDeliveryProofEvent();
            final LogisticTrustEvent e2 = buildDeliveryProofEvent();

            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            StepVerifier.create(publisherAdapter.publishAll(java.util.List.of(e1, e2)))
                    .verifyComplete();

            verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private LogisticTrustEvent buildDeliveryProofEvent() {
        final com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord proof =
                new com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord(
                        "proof-" + java.util.UUID.randomUUID(),
                        "mission-001", "package-001", "actor-001", "tenant-001",
                        "a".repeat(64), null, 3.848, 11.502, LocalDateTime.now());
        return LogisticTrustEvent.forDeliveryProof(proof, "mission-001", "actor-001");
    }
}
