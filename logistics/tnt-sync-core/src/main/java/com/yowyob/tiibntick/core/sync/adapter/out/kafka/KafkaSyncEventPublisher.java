package com.yowyob.tiibntick.core.sync.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.domain.event.EntityVersionChangedEvent;
import com.yowyob.tiibntick.core.sync.domain.event.SyncCompletedEvent;
import com.yowyob.tiibntick.core.sync.domain.event.SyncConflictDetectedEvent;
import com.yowyob.tiibntick.core.sync.domain.event.SyncDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Outbox-backed adapter implementing {@link ISyncEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly via {@code KafkaTemplate}. Unlike tnt-realtime-core's telemetry
 * events (deliberately left unmigrated — high-frequency, ephemeral, no lasting business
 * consequence when lost), tnt-sync-core's events are the durability backbone of the offline-first
 * sync protocol itself: a lost {@code SyncConflictDetectedEvent}/{@code SyncCompletedEvent} can
 * mean a client device never learns its conflict was resolved, or a push-sync silently fails to
 * notify downstream consumers — exactly the kind of event this migration exists to protect.
 * Envelopes are persisted in the same DB transaction as the business write (see the
 * {@code @Transactional} boundary on {@code SyncBatchApplicationService.processSyncBatch}), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ.
 *
 * <p>The Kafka wire format is unchanged: each event is still serialized as the raw domain event
 * JSON with the event id as the record key — only the transport changed, so existing consumers
 * require no change.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaSyncEventPublisher implements ISyncEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaSyncEventPublisher.class);

    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public KafkaSyncEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(SyncDomainEvent event) {
        String aggregateId;
        String aggregateType;
        switch (event) {
            case EntityVersionChangedEvent e -> {
                aggregateId = e.getAggregateId();
                aggregateType = e.getAggregateType();
            }
            case SyncConflictDetectedEvent e -> {
                aggregateId = e.getAggregateId();
                aggregateType = e.getAggregateType();
            }
            case SyncCompletedEvent e -> {
                aggregateId = e.getSessionId();
                aggregateType = "SyncSession";
            }
            default -> {
                aggregateId = event.getEventId();
                aggregateType = "SyncEvent";
            }
        }

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(event.getEventId())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(aggregateId)
                        .aggregateType(aggregateType)
                        .tenantId(event.getTenantId())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(event.kafkaTopic())
                        // Pre-migration adapter keyed every record by eventId (send(topic,
                        // event.getEventId(), json)) — preserve that partitioning/ordering
                        // contract for existing consumers rather than defaulting to aggregateId.
                        .kafkaPartitionKey(event.getEventId())
                        .occurredAt(event.getOccurredAt() != null
                                ? event.getOccurredAt()
                                : LocalDateTime.now(ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued sync event {} to outbox topic {}",
                        event.getEventId(), event.kafkaTopic()))
                .doOnError(ex -> log.error("Failed to enqueue sync event {} to outbox: {}",
                        event.getEventId(), ex.getMessage()));
    }
}
