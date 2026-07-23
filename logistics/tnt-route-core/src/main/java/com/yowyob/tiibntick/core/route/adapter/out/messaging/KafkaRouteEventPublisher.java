package com.yowyob.tiibntick.core.route.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.route.application.port.out.IRouteEventPublisher;
import com.yowyob.tiibntick.core.route.domain.event.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Outbox-backed adapter for publishing tnt-route-core domain events.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * {@link #publishTourOptimized}, {@link #publishReroutingTriggered} and
 * {@link #publishVrpFallback} now delegate to {@link PublishEventUseCase} (yow-event-kernel's
 * transactional outbox) instead of sending to Kafka directly — these are business decisions
 * (a completed tour plan, a rerouting decision, a VRP solver fallback) worth retrying/replaying.
 *
 * <p><strong>Deliberately NOT migrated — {@link #publishEtaUpdated}:</strong> ETA recomputation
 * fires on every GPS/Kalman-filter update (high-frequency, ephemeral positional telemetry consumed
 * live by tnt-realtime-core's websocket push — {@code TntTopics#ROUTE_ETA_UPDATED}); a lost ETA
 * update has no lasting business consequence since the next GPS ping supersedes it within seconds,
 * so routing it through the durable outbox would only add write amplification for no durability
 * benefit. It is also the one event of the four whose {@code tenantId} is never actually populated
 * by its caller ({@code KalmanFilterService.updateEta} passes {@code null} — a pre-existing gap,
 * since {@link com.yowyob.tiibntick.core.route.application.port.in.IUpdateEtaUseCase} has no
 * tenant-scoped signature), which would violate the outbox envelope's non-null tenant invariant.
 * Left on direct {@code KafkaTemplate} send, same as before — flagged here for the Chantier C
 * orchestrator to confirm rather than silently migrated.
 *
 * <p>The Kafka wire format for the three migrated events is unchanged: each is still serialized
 * as the raw domain event JSON, with the same partition key as before — only the transport
 * changed, so existing consumers require no change.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaRouteEventPublisher implements IRouteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaRouteEventPublisher.class);

    public static final String TOPIC_TOUR    = TntTopics.ROUTE_TOUR_EVENTS;
    public static final String TOPIC_ETA     = TntTopics.ROUTE_ETA_UPDATED;
    public static final String TOPIC_REROUTE = TntTopics.ROUTE_REROUTE_EVENTS;
    public static final String TOPIC_VRP     = TntTopics.ROUTE_VRP_EVENTS;

    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaRouteEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("routeKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("routeObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishTourOptimized(TourOptimizedEvent e) {
        return enqueue(TOPIC_TOUR, e.eventId().toString(), e.delivererId(), "Tour",
                e.tenantId().toString(), e.occurredAt(), e);
    }

    @Override
    public Mono<Void> publishEtaUpdated(EtaUpdatedEvent e) {
        // Deliberately not migrated to the outbox — see class Javadoc.
        return send(TOPIC_ETA, e.missionId(), e);
    }

    @Override
    public Mono<Void> publishReroutingTriggered(ReroutingTriggeredEvent e) {
        return enqueue(TOPIC_REROUTE, e.eventId().toString(), e.missionId(), "Mission",
                e.tenantId().toString(), e.occurredAt(), e);
    }

    @Override
    public Mono<Void> publishVrpFallback(VrpFallbackActivatedEvent e) {
        return enqueue(TOPIC_VRP, e.eventId().toString(), e.tenantId().toString(), "VrpRun",
                e.tenantId().toString(), e.occurredAt(), e);
    }

    private Mono<Void> enqueue(String topic, String correlationId, String aggregateId,
                               String aggregateType, String tenantId,
                               java.time.Instant occurredAt, Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(correlationId)
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(aggregateId)
                        .aggregateType(aggregateType)
                        .tenantId(tenantId)
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(topic)
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event={} aggregateId={} topic={} to outbox",
                        event.getClass().getSimpleName(), aggregateId, topic))
                .doOnError(ex -> log.error("Failed to enqueue event={} to outbox: {}",
                        event.getClass().getSimpleName(), ex.getMessage()));
    }

    private Mono<Void> send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return Mono.fromFuture(kafkaTemplate.send(new ProducerRecord<>(topic, key, json))).then();
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }
}
