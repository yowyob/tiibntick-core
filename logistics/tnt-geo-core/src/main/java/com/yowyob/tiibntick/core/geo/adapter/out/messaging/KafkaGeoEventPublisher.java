package com.yowyob.tiibntick.core.geo.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.geo.application.port.out.IGeoEventPublisher;
import com.yowyob.tiibntick.core.geo.domain.event.RoadNodeCreatedEvent;
import com.yowyob.tiibntick.core.geo.domain.event.ServiceZoneUpdatedEvent;
import com.yowyob.tiibntick.core.geo.domain.event.TrafficConditionChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed adapter for publishing tnt-geo-core domain events.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly via {@code KafkaTemplate}. Envelopes are persisted in the same DB
 * transaction as the business write (see the {@code @Transactional} boundaries in
 * {@code RoadNetworkService}/{@code GeofencingService}/{@code CostFunctionService}), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ.
 *
 * <p>The Kafka wire format is unchanged: each event is still serialized as the raw domain event
 * JSON, with the tenant id as the record key (preserved via {@code kafkaPartitionKey}) — only
 * the transport changed, so existing consumers require no change.
 *
 * <p>Topics:
 *   tnt.geo.traffic.events   — TrafficConditionChangedEvent (consumed by tnt-route-core)
 *   tnt.geo.node.events      — RoadNodeCreatedEvent (consumed by tnt-search)
 *   tnt.geo.zone.events      — ServiceZoneUpdatedEvent (consumed by tnt-actor-core, tnt-delivery-core)
 *   tnt.geo.alert.created    — same TrafficConditionChangedEvent, additionally, whenever it
 *                              reaches this adapter at all (consumed by tnt-sync-core)
 *
 * <p>{@code publishTrafficChanged} is only ever invoked by
 * {@code CostFunctionService#updateTrafficAndPublishIfSignificant} when
 * {@link TrafficConditionChangedEvent#isSignificant()} is true (&gt;20% congestion swing) —
 * every call already represents what the domain considers alert-worthy, so it fans out to
 * {@link TntTopics#GEO_ALERT_CREATED} too. Fixed 2026-07-23 (Audit n5 P-01): that topic
 * previously had no producer at all.
 *
 * Author: MANFOUO Braun
 */
@Component
public class KafkaGeoEventPublisher implements IGeoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaGeoEventPublisher.class);

    public static final String TOPIC_TRAFFIC    = "tnt.geo.traffic.events";
    public static final String TOPIC_NODE       = "tnt.geo.node.events";
    public static final String TOPIC_ZONE       = "tnt.geo.zone.events";

    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public KafkaGeoEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("geoObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishTrafficChanged(TrafficConditionChangedEvent event) {
        return enqueue(TOPIC_TRAFFIC, event.eventId(), event.tenantId(),
                event.arcId(), "RoadArc", event.occurredAt(), event)
                .then(enqueue(TntTopics.GEO_ALERT_CREATED, event.eventId(), event.tenantId(),
                        event.arcId(), "RoadArc", event.occurredAt(), event));
    }

    @Override
    public Mono<Void> publishRoadNodeCreated(RoadNodeCreatedEvent event) {
        return enqueue(TOPIC_NODE, event.eventId(), event.tenantId(),
                event.nodeId(), "RoadNode", event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishServiceZoneUpdated(ServiceZoneUpdatedEvent event) {
        return enqueue(TOPIC_ZONE, event.eventId(), event.tenantId(),
                event.zoneId().toString(), "ServiceZone", event.occurredAt(), event);
    }

    private Mono<Void> enqueue(String topic, UUID eventId, UUID tenantId,
                               String aggregateId, String aggregateType,
                               Instant occurredAt, Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(eventId.toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(aggregateId)
                        .aggregateType(aggregateType)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(topic)
                        // Pre-migration adapter keyed records by tenantId — preserve that
                        // partitioning contract for existing consumers.
                        .kafkaPartitionKey(tenantId.toString())
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event={} aggregateId={} topic={} to outbox",
                        event.getClass().getSimpleName(), aggregateId, topic))
                .doOnError(ex -> log.error("Failed to enqueue event={} to outbox: {}",
                        event.getClass().getSimpleName(), ex.getMessage()));
    }
}
