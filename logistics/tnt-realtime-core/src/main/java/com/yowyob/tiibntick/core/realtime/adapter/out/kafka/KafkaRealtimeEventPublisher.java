package com.yowyob.tiibntick.core.realtime.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.domain.event.GeofenceTriggerEvent;
import com.yowyob.tiibntick.core.realtime.domain.event.RealtimeDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Kafka adapter implementing {@link IRealtimeEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * only {@link GeofenceTriggerEvent} is migrated to yow-event-kernel's transactional outbox
 * ({@link PublishEventUseCase}) — a zone crossing drives a real, one-shot state transition in
 * tnt-delivery-core (e.g. auto-starting the hub-deposit flow), so a lost event has a lasting
 * business consequence worth retrying/replaying.
 *
 * <p><strong>Deliberately NOT migrated — everything else this adapter publishes</strong>
 * ({@code GpsPositionUpdatedEvent}, {@code ETAUpdatedEvent}, {@code ActorConnectedEvent},
 * {@code ActorDisconnectedEvent}): these are high-frequency, ephemeral, "next update supersedes
 * this one" telemetry (a GPS ping fires every few seconds per active deliverer; an actor
 * connect/disconnect self-heals via the next heartbeat) with no lasting business consequence
 * when a single message is lost. Routing every GPS ping through a durable, transactional DB
 * write would add write amplification and latency to a pipeline whose entire purpose is
 * low-latency live tracking — a poor fit for the outbox, unlike tnt-sync-core's events (see that
 * module's migration) which are the durability backbone of an offline-first protocol. These four
 * event types stay on direct {@code KafkaTemplate} send, unchanged from before this migration —
 * flagged here for the Chantier C orchestrator rather than silently decided.
 *
 * <p>The event ID is used as the Kafka message key for ordering guarantees within a partition,
 * for both the migrated and non-migrated paths — wire format is unchanged throughout.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaRealtimeEventPublisher implements IRealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaRealtimeEventPublisher.class);

    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaRealtimeEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("realtimeKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(RealtimeDomainEvent event) {
        if (event instanceof GeofenceTriggerEvent geofenceEvent) {
            return enqueue(geofenceEvent);
        }
        return sendDirect(event);
    }

    // ── Migrated path: GeofenceTriggerEvent → transactional outbox ──────────────

    private Mono<Void> enqueue(GeofenceTriggerEvent event) {
        String actorId = event.getTrigger().actorId();
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(resolveCorrelationId(event))
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(actorId)
                        .aggregateType("Actor")
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
                .doOnSuccess(v -> log.debug("Enqueued geofence event {} actor={} to outbox",
                        event.getEventId(), actorId))
                .doOnError(ex -> log.error("Failed to enqueue geofence event {} to outbox: {}",
                        event.getEventId(), ex.getMessage()));
    }

    private String resolveCorrelationId(RealtimeDomainEvent event) {
        return event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString();
    }

    // ── Unmigrated path: ephemeral high-frequency telemetry → direct Kafka send ─

    private Mono<Void> sendDirect(RealtimeDomainEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(json -> Mono.fromFuture(kafkaTemplate.send(event.kafkaTopic(), event.getEventId(), json)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> log.debug("Published event {} to topic {}",
                        event.getEventId(), event.kafkaTopic()))
                .doOnError(ex -> log.error("Failed to publish event {} to topic {}: {}",
                        event.getEventId(), event.kafkaTopic(), ex.getMessage()))
                .onErrorResume(JsonProcessingException.class, ex -> Mono.error(
                        new RuntimeException("Failed to serialize event: " + event.getEventId(), ex)))
                .then();
    }
}
