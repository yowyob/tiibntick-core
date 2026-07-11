package com.yowyob.tiibntick.core.trust.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.domain.service.TrustEventEnvelopeMapper;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustEventPublisherPort;

import java.util.List;
import java.util.Map;

/**
 * Kafka Adapter — {@code KafkaTrustEventPublisherAdapter}.
 *
 * <p>Implements {@link TrustEventPublisherPort} by serializing
 * {@link LogisticTrustEvent} instances into the standard
 * {@code TrustEventKafkaMessage} JSON format and publishing them to the
 * {@code yow.trust.events} topic.
 *
 * <h3>Message Structure</h3>
 * <p>The Kafka message JSON conforms to the {@code TrustEventKafkaMessage}
 * contract of {@code yow-trust-event}:
 * <pre>
 * {
 *   "correlationId":  "uuid",
 *   "tenantId":       "uuid",
 *   "solutionCode":   "TNT",
 *   "eventType":      "DELIVERY_PROOF_RECORDED",
 *   "entityType":     "DELIVERY_PROOF",
 *   "entityId":       "uuid",
 *   "payload":        "{...logistic context...}",
 *   "sourceService":  "tnt-trust",
 *   "occurredAt":     "2025-01-01T12:00:00"
 * }
 * </pre>
 *
 * <h3>Message Key</h3>
 * <p>The Kafka message key is the {@code entityId}, ensuring that all events
 * for the same entity are routed to the same Kafka partition (ordering guarantee).
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Component
public class KafkaTrustEventPublisherAdapter implements TrustEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaTrustEventPublisherAdapter.class);

    /** Kafka topic consumed by yow-trust-event. */
    public static final String TRUST_EVENTS_TOPIC = "yow.trust.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public KafkaTrustEventPublisherAdapter(
            @Qualifier("tntTrustKafkaTemplate") final KafkaTemplate<String, String> kafkaTemplate,
            final ObjectMapper objectMapper,
            final MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publish(final LogisticTrustEvent event) {
        return Mono.fromRunnable(() -> doPublish(event))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishAll(final List<LogisticTrustEvent> events) {
        return events.stream()
                .reduce(Mono.<Void>empty(),
                        (acc, e) -> acc.then(publish(e)),
                        (a, b) -> a.then(b));
    }

    /**
     * Serializes a {@link LogisticTrustEvent} into the wire-format JSON envelope,
     * without sending it. Used by {@code LogisticEventPublisherService} to persist
     * the exact payload that would have been sent to Kafka into the local
     * {@code trust_retry_queue} when the gateway is degraded (see resilience §15
     * of the Trust connexion design — {@code TNT_CORE_Connexion_Trust_Module.md}).
     *
     * @param event the logistic trust event to serialize
     * @return the JSON envelope string, ready to be republished verbatim via {@link #republish}
     */
    public String toEnvelopeJson(final LogisticTrustEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(TrustEventEnvelopeMapper.toEnvelope(event));
    }

    /**
     * Re-sends a previously-serialized envelope verbatim to {@value #TRUST_EVENTS_TOPIC}.
     * Used exclusively by {@code TrustRetryQueueDrainer} to drain
     * {@code trust_retry_queue} rows once {@code yow-trust-event}/Kafka connectivity
     * is confirmed available again — bypasses {@link LogisticTrustEvent} entirely
     * since the retry queue stores the wire envelope, not the domain object.
     *
     * @param messageKey  the Kafka partition key (the original {@code entityId})
     * @param messageJson the pre-serialized JSON envelope
     * @return a {@link Mono} completing when the Kafka send has been acknowledged
     */
    public Mono<Void> republish(final String messageKey, final String messageJson) {
        return Mono.fromFuture(() -> kafkaTemplate.send(TRUST_EVENTS_TOPIC, messageKey, messageJson))
                .doOnNext(result -> log.debug("Retry-queue drain: republished to Kafka — key={}, offset={}",
                        messageKey, result.getRecordMetadata().offset()))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Serializes and sends the event to Kafka.
     * Uses the {@code entityId} as the message key to guarantee per-entity ordering.
     */
    private void doPublish(final LogisticTrustEvent event) {
        try {
            final Map<String, Object> message = TrustEventEnvelopeMapper.toEnvelope(event);
            final String json = objectMapper.writeValueAsString(message);

            // Key = entityId → same-partition ordering for the same entity
            kafkaTemplate.send(TRUST_EVENTS_TOPIC, event.getEntityId(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish event correlationId={}: {}",
                                    event.getCorrelationId(), ex.getMessage());
                            meterRegistry.counter("tnt.trust.kafka.publish.error",
                                    "type", event.getLogisticEventType().name()).increment();
                        } else {
                            log.debug("Published to Kafka — correlationId={}, offset={}",
                                    event.getCorrelationId(),
                                    result.getRecordMetadata().offset());
                        }
                    });

        } catch (final Exception e) {
            log.error("Failed to serialize LogisticTrustEvent correlationId={}: {}",
                    event.getCorrelationId(), e.getMessage());
            throw new RuntimeException("Kafka publish failed for correlationId="
                    + event.getCorrelationId(), e);
        }
    }
}
