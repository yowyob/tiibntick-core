package com.yowyob.tiibntick.core.trust.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaTrustEventPublisherAdapter} — the outbox-backed
 * publisher (Chantier C · Audit n°3 · P5). Verifies that the adapter maps a
 * {@link LogisticTrustEvent} to a {@link DomainEventEnvelope} carrying the
 * exact same {@code TrustEventKafkaMessage} wire payload previously sent
 * directly to Kafka, routed to {@code yow.trust.events} with the entityId as
 * partition key.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("tnt-trust — Kafka Adapter Tests")
class KafkaAdapterTest {

    @Mock private PublishEventUseCase publishEventUseCase;
    @Mock private PublishEventBatchUseCase publishEventBatchUseCase;

    private ObjectMapper objectMapper;
    private KafkaTrustEventPublisherAdapter publisherAdapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        publisherAdapter = new KafkaTrustEventPublisherAdapter(
                publishEventUseCase, publishEventBatchUseCase, objectMapper);
    }

    // ── KafkaTrustEventPublisherAdapter ────────────────────────────────────────

    @Nested
    @DisplayName("KafkaTrustEventPublisherAdapter")
    class PublisherAdapterTest {

        @Test
        @DisplayName("publish() should enqueue an outbox envelope routed to yow.trust.events with entityId as key")
        void shouldEnqueueOutboxEnvelopeWithCorrectRouting() {
            final LogisticTrustEvent event = buildDeliveryProofEvent();

            when(publishEventUseCase.publish(any(DomainEventEnvelope.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(publisherAdapter.publish(event))
                    .verifyComplete();

            final ArgumentCaptor<DomainEventEnvelope> envelopeCaptor =
                    ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(publishEventUseCase).publish(envelopeCaptor.capture());
            final DomainEventEnvelope envelope = envelopeCaptor.getValue();

            assertThat(envelope.getKafkaTopic()).isEqualTo("yow.trust.events");
            assertThat(envelope.getKafkaPartitionKey()).isEqualTo(event.getEntityId());
            assertThat(envelope.getAggregateId()).isEqualTo(event.getEntityId());
            assertThat(envelope.getAggregateType()).isEqualTo("DELIVERY_PROOF");
            assertThat(envelope.getEventType()).isEqualTo("DELIVERY_PROOF_RECORDED");
            assertThat(envelope.getTenantId()).isEqualTo("tenant-001");
            assertThat(envelope.getSolutionCode()).isEqualTo("TNT");
            assertThat(envelope.getCorrelationId()).isEqualTo(event.getCorrelationId());
        }

        @Test
        @DisplayName("publish() should keep the exact TrustEventKafkaMessage wire payload")
        void shouldPreserveWireFormatPayload() throws Exception {
            final LogisticTrustEvent event = buildDeliveryProofEvent();

            when(publishEventUseCase.publish(any(DomainEventEnvelope.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(publisherAdapter.publish(event))
                    .verifyComplete();

            final ArgumentCaptor<DomainEventEnvelope> envelopeCaptor =
                    ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(publishEventUseCase).publish(envelopeCaptor.capture());

            final JsonNode wire = objectMapper.readTree(envelopeCaptor.getValue().getPayload());
            assertThat(wire.get("correlationId").asText()).isEqualTo(event.getCorrelationId());
            assertThat(wire.get("tenantId").asText()).isEqualTo("tenant-001");
            assertThat(wire.get("solutionCode").asText()).isEqualTo("TNT");
            assertThat(wire.get("eventType").asText()).isEqualTo("DELIVERY_PROOF_RECORDED");
            assertThat(wire.get("entityType").asText()).isEqualTo("DELIVERY_PROOF");
            assertThat(wire.get("entityId").asText()).isEqualTo(event.getEntityId());
            assertThat(wire.get("sourceService").asText()).isEqualTo("tnt-trust");
            assertThat(wire.get("payload").asText()).contains("proofId");
            assertThat(wire.has("occurredAt")).isTrue();
        }

        @Test
        @DisplayName("publishAll() should enqueue all events as one atomic outbox batch")
        void shouldEnqueueBatchAtomically() {
            final LogisticTrustEvent e1 = buildDeliveryProofEvent();
            final LogisticTrustEvent e2 = buildDeliveryProofEvent();

            when(publishEventBatchUseCase.publishAll(anyList()))
                    .thenReturn(Mono.just(2));

            StepVerifier.create(publisherAdapter.publishAll(List.of(e1, e2)))
                    .verifyComplete();

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<DomainEventEnvelope>> batchCaptor =
                    ArgumentCaptor.forClass(List.class);
            verify(publishEventBatchUseCase, times(1)).publishAll(batchCaptor.capture());
            verify(publishEventUseCase, never()).publish(any());

            assertThat(batchCaptor.getValue()).hasSize(2);
            assertThat(batchCaptor.getValue())
                    .extracting(DomainEventEnvelope::getCorrelationId)
                    .containsExactly(e1.getCorrelationId(), e2.getCorrelationId());
        }

        @Test
        @DisplayName("publishAll() should complete empty without calling the batch use case for null/empty lists")
        void shouldGuardEmptyBatch() {
            StepVerifier.create(publisherAdapter.publishAll(List.of()))
                    .verifyComplete();
            StepVerifier.create(publisherAdapter.publishAll(null))
                    .verifyComplete();

            verifyNoInteractions(publishEventBatchUseCase, publishEventUseCase);
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
