package com.yowyob.tiibntick.core.incident.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed implementation of {@link IIncidentEventPublisher} publishing all twelve
 * domain events to their dedicated topics.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly via {@code KafkaTemplate} (which was fire-and-forget: the send future
 * was never even awaited). Envelopes are persisted in the same DB transaction as the business
 * write (see the {@code @Transactional} boundaries in the incident application services), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ.
 *
 * <p>The Kafka wire format is unchanged: each event is still serialized as the raw domain event
 * JSON with the incident id as the record key — only the transport changed, so existing
 * consumers require no change.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */
@Slf4j
@Component
public class IncidentKafkaEventPublisher implements IIncidentEventPublisher {

    private static final String AGGREGATE_TYPE = "Incident";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public IncidentKafkaEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("incidentObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    private static final String TOPIC_CREATED          = "tnt.incident.created";
    private static final String TOPIC_STATUS_CHANGED   = "tnt.incident.status.changed";
    private static final String TOPIC_TRIAGED           = "tnt.incident.triaged";
    private static final String TOPIC_DRIVER_ASSIGNED   = "tnt.incident.driver.assigned";
    private static final String TOPIC_HANDOVER_DONE     = "tnt.incident.handover.completed";
    private static final String TOPIC_RESOLVED          = "tnt.incident.resolved";
    private static final String TOPIC_CLOSED            = "tnt.incident.closed";
    private static final String TOPIC_CANCELLED         = "tnt.incident.cancelled";
    private static final String TOPIC_ESCALATED         = "tnt.incident.escalated";
    private static final String TOPIC_ESCALATED_DISPUTE = "tnt.incident.escalated.to.dispute";
    private static final String TOPIC_INTERAGENCY_REQ   = "tnt.incident.interagency.requested";
    private static final String TOPIC_INTERAGENCY_DONE  = "tnt.incident.interagency.completed";

    @Override
    public Mono<Void> publish(IncidentCreatedEvent event) {
        return enqueue(TOPIC_CREATED, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(IncidentStatusChangedEvent event) {
        return enqueue(TOPIC_STATUS_CHANGED, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(IncidentTriagedEvent event) {
        return enqueue(TOPIC_TRIAGED, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(IncidentDriverAssignedEvent event) {
        return enqueue(TOPIC_DRIVER_ASSIGNED, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(HandoverCompletedEvent event) {
        return enqueue(TOPIC_HANDOVER_DONE, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(IncidentResolvedEvent event) {
        return enqueue(TOPIC_RESOLVED, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(IncidentClosedEvent event) {
        return enqueue(TOPIC_CLOSED, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(IncidentCancelledEvent event) {
        return enqueue(TOPIC_CANCELLED, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(IncidentEscalatedEvent event) {
        return enqueue(TOPIC_ESCALATED, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(IncidentEscalatedToDisputeEvent event) {
        return enqueue(TOPIC_ESCALATED_DISPUTE, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(InterAgencyCoopRequestedEvent event) {
        return enqueue(TOPIC_INTERAGENCY_REQ, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    @Override
    public Mono<Void> publish(InterAgencyCoopCompletedEvent event) {
        return enqueue(TOPIC_INTERAGENCY_DONE, event.getEventId(), event.getIncidentId(),
                event.getTenantId(), event.getOccurredAt(), event);
    }

    private Mono<Void> enqueue(String topic, UUID eventId, UUID incidentId,
                               UUID tenantId, Instant occurredAt, Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(eventId != null ? eventId.toString() : UUID.randomUUID().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(incidentId.toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(topic)
                        .occurredAt(occurredAt != null
                                ? LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)
                                : LocalDateTime.now(ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event={} incidentId={} topic={} to outbox",
                        event.getClass().getSimpleName(), incidentId, topic))
                .doOnError(ex -> log.error("Failed to enqueue event={} incidentId={} to outbox: {}",
                        event.getClass().getSimpleName(), incidentId, ex.getMessage()));
    }
}
