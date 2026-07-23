package com.yowyob.tiibntick.core.trust.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.domain.service.TrustEventEnvelopeMapper;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustEventPublisherPort;

import java.util.List;

/**
 * Outbox-backed adapter — {@code KafkaTrustEventPublisherAdapter}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): implements
 * {@link TrustEventPublisherPort} by delegating to {@link PublishEventUseCase}/
 * {@link PublishEventBatchUseCase} (yow-event-kernel's transactional outbox)
 * instead of sending to Kafka directly. Envelopes are persisted in the same DB
 * transaction as the business write (see the {@code @Transactional} boundaries
 * in the trust chain services), and {@code OutboxPollerService} relays them to
 * Kafka asynchronously with retry/DLQ — an anchoring record can no longer be
 * saved while its trust event is silently lost.
 *
 * <p>This supersedes the module's previous bespoke resilience mechanism
 * (availability guard + {@code trustEventGatewayWrite} circuit breaker +
 * {@code trust_retry_queue} drain, §15 of the Trust connexion design): the
 * outbox provides the same no-event-lost guarantee with strictly stronger
 * atomicity, so that mechanism has been removed.
 *
 * <h3>Message Structure — unchanged wire format</h3>
 * <p>The Kafka message body is still the exact {@code TrustEventKafkaMessage}
 * JSON contract of {@code yow-trust-event}, produced by
 * {@link TrustEventEnvelopeMapper}:
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
 * Only the transport changed (outbox instead of direct {@code KafkaTemplate}),
 * so the consuming {@code yow-trust-event} Kernel microservice requires no change.
 *
 * <h3>Message Key</h3>
 * <p>The Kafka message key is still the {@code entityId} (the envelope's
 * {@code kafkaPartitionKey} defaults to its {@code aggregateId}), ensuring that
 * all events for the same entity are routed to the same Kafka partition
 * (ordering guarantee).
 *
 * @author MANFOUO Braun
 * @version 2.0
 */
@Component
public class KafkaTrustEventPublisherAdapter implements TrustEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaTrustEventPublisherAdapter.class);

    /** Kafka topic consumed by yow-trust-event. */
    public static final String TRUST_EVENTS_TOPIC = "yow.trust.events";

    private static final String SOLUTION_CODE = LogisticTrustEvent.SOLUTION_CODE;

    private final PublishEventUseCase publishEventUseCase;
    private final PublishEventBatchUseCase publishEventBatchUseCase;
    private final ObjectMapper objectMapper;

    public KafkaTrustEventPublisherAdapter(
            final PublishEventUseCase publishEventUseCase,
            final PublishEventBatchUseCase publishEventBatchUseCase,
            final ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.publishEventBatchUseCase = publishEventBatchUseCase;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publish(final LogisticTrustEvent event) {
        return Mono.fromCallable(() -> toOutboxEnvelope(event))
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued trust event to outbox — correlationId={}, type={}",
                        event.getCorrelationId(), event.getLogisticEventType()))
                .doOnError(ex -> log.error("Failed to enqueue trust event to outbox — correlationId={}",
                        event.getCorrelationId(), ex));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishAll(final List<LogisticTrustEvent> events) {
        if (events == null || events.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(events)
                .map(this::toOutboxEnvelope)
                .collectList()
                .flatMap(publishEventBatchUseCase::publishAll)
                .then();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Wraps a {@link LogisticTrustEvent} into the outbox's
     * {@link DomainEventEnvelope}. The envelope payload is the exact same
     * {@code TrustEventKafkaMessage} JSON previously sent directly to Kafka;
     * the envelope's own metadata only drives outbox routing (topic, partition
     * key, tenant, correlation id).
     */
    private DomainEventEnvelope toOutboxEnvelope(final LogisticTrustEvent event) {
        try {
            final String payload = objectMapper.writeValueAsString(TrustEventEnvelopeMapper.toEnvelope(event));
            return DomainEventEnvelope.wrap()
                    .correlationId(event.getCorrelationId())
                    .eventType(event.toKernelEventType())
                    .aggregateId(event.getEntityId())
                    .aggregateType(event.getEntityType())
                    .tenantId(event.getTenantId())
                    .solutionCode(SOLUTION_CODE)
                    .payload(payload)
                    .kafkaTopic(TRUST_EVENTS_TOPIC)
                    .occurredAt(event.getOccurredAt())
                    .build();
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException(
                    "Cannot serialize LogisticTrustEvent correlationId=" + event.getCorrelationId(), e);
        }
    }
}
