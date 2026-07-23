package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeEventPublisher;
import com.yowyob.tiibntick.core.dispute.domain.event.*;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link IDisputeEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase}/{@link PublishEventBatchUseCase} (yow-event-kernel's
 * transactional outbox) instead of sending to Kafka directly via {@code KafkaTemplate}. Envelopes
 * are persisted in the same DB transaction as the business write (see the {@code @Transactional}
 * boundaries already present on {@code DisputeCommandService}/{@code EvidenceApplicationService}),
 * and {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ.
 *
 * <p>The Kafka wire format is unchanged: each event is still serialized as the raw domain event
 * JSON (no extra envelope wrapping) — only the transport changed (outbox instead of direct
 * {@code KafkaTemplate.send}), so existing consumers require no change.
 *
 * @author MANFOUO Braun
 */
@Component
public class DisputeKafkaEventPublisher implements IDisputeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DisputeKafkaEventPublisher.class);

    static final String TOPIC_OPENED = "tnt.dispute.opened";
    static final String TOPIC_STATUS_CHANGED = "tnt.dispute.status.changed";
    static final String TOPIC_EVIDENCE_SUBMITTED = "tnt.dispute.evidence.submitted";
    static final String TOPIC_RULED = "tnt.dispute.ruled";
    static final String TOPIC_ESCALATED = "tnt.dispute.escalated";
    static final String TOPIC_COMPENSATION_PROCESSED = "tnt.dispute.compensation.processed";
    static final String TOPIC_CLOSED = "tnt.dispute.closed";

    private static final String AGGREGATE_TYPE = "Dispute";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final PublishEventBatchUseCase publishEventBatchUseCase;
    private final ObjectMapper objectMapper;

    public DisputeKafkaEventPublisher(
            PublishEventUseCase publishEventUseCase,
            PublishEventBatchUseCase publishEventBatchUseCase,
            @Qualifier("disputeObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.publishEventBatchUseCase = publishEventBatchUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishDisputeOpened(DisputeOpened event) {
        return enqueue(TOPIC_OPENED, event.disputeId().getValue(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishMediatorAssigned(MediatorAssigned event) {
        return enqueue(TOPIC_STATUS_CHANGED, event.disputeId().getValue(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishEvidenceSubmitted(EvidenceSubmitted event) {
        return enqueue(TOPIC_EVIDENCE_SUBMITTED, event.disputeId().getValue(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishDisputeRuled(DisputeRuled event) {
        return enqueue(TOPIC_RULED, event.disputeId().getValue(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishDisputeEscalated(DisputeEscalated event) {
        return enqueue(TOPIC_ESCALATED, event.disputeId().getValue(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishCompensationProcessed(CompensationProcessed event) {
        return enqueue(TOPIC_COMPENSATION_PROCESSED, event.disputeId().getValue(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishDisputeClosed(DisputeClosed event) {
        return enqueue(TOPIC_CLOSED, event.disputeId().getValue(), event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishAll(Dispute dispute) {
        List<Object> events = List.copyOf(dispute.getDomainEvents());
        if (events.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(events)
                .flatMap(this::toEnvelope)
                .collectList()
                .flatMap(envelopes -> envelopes.isEmpty()
                        ? Mono.<Integer>empty()
                        : publishEventBatchUseCase.publishAll(envelopes))
                .then()
                .doOnSuccess(v -> dispute.clearDomainEvents());
    }

    // =========================================================================
    // PRIVATE
    // =========================================================================

    private Mono<DomainEventEnvelope> toEnvelope(Object event) {
        return switch (event) {
            case DisputeOpened e -> serialize(e).map(payload -> buildEnvelope(
                    TOPIC_OPENED, e.disputeId().getValue(), e.tenantId(), e.occurredAt(), e, payload));
            case MediatorAssigned e -> serialize(e).map(payload -> buildEnvelope(
                    TOPIC_STATUS_CHANGED, e.disputeId().getValue(), e.tenantId(), e.occurredAt(), e, payload));
            case EvidenceSubmitted e -> serialize(e).map(payload -> buildEnvelope(
                    TOPIC_EVIDENCE_SUBMITTED, e.disputeId().getValue(), e.tenantId(), e.occurredAt(), e, payload));
            case DisputeRuled e -> serialize(e).map(payload -> buildEnvelope(
                    TOPIC_RULED, e.disputeId().getValue(), e.tenantId(), e.occurredAt(), e, payload));
            case DisputeEscalated e -> serialize(e).map(payload -> buildEnvelope(
                    TOPIC_ESCALATED, e.disputeId().getValue(), e.tenantId(), e.occurredAt(), e, payload));
            case CompensationProcessed e -> serialize(e).map(payload -> buildEnvelope(
                    TOPIC_COMPENSATION_PROCESSED, e.disputeId().getValue(), e.tenantId(), e.occurredAt(), e, payload));
            case DisputeClosed e -> serialize(e).map(payload -> buildEnvelope(
                    TOPIC_CLOSED, e.disputeId().getValue(), e.tenantId(), e.occurredAt(), e, payload));
            default -> {
                log.warn("Unknown domain event type: {}", event.getClass().getSimpleName());
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> enqueue(
            String topic, String disputeId, String tenantId, LocalDateTime occurredAt, Object event) {
        return serialize(event)
                .map(payload -> buildEnvelope(topic, disputeId, tenantId, occurredAt, event, payload))
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event={} disputeId={} topic={} to outbox",
                        event.getClass().getSimpleName(), disputeId, topic))
                .doOnError(e -> log.error("Failed to enqueue event={} disputeId={} to outbox: {}",
                        event.getClass().getSimpleName(), disputeId, e.getMessage()));
    }

    private DomainEventEnvelope buildEnvelope(
            String topic, String disputeId, String tenantId, LocalDateTime occurredAt, Object event, String payload) {
        return DomainEventEnvelope.wrap()
                .correlationId(UUID.randomUUID().toString())
                .eventType(event.getClass().getSimpleName())
                .aggregateId(disputeId)
                .aggregateType(AGGREGATE_TYPE)
                .tenantId(tenantId)
                .solutionCode(SOLUTION_CODE)
                .payload(payload)
                .kafkaTopic(topic)
                .occurredAt(occurredAt != null ? occurredAt : LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private Mono<String> serialize(Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .doOnError(e -> log.error("Failed to serialize event {}: {}",
                        event.getClass().getSimpleName(), e.getMessage()));
    }
}
