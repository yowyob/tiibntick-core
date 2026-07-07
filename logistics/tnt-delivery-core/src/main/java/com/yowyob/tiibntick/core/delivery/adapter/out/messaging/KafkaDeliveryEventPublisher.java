package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.FreelancerOrgAssignedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.MissionStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Kafka adapter implementing {@link DeliveryEventPublisher}.
 *
 * <p>Each domain event is serialized to JSON and published to its topic:
 * {@code tnt.delivery.events}. The topic name follows the pattern
 * {@code tnt.{aggregate}.{action}} for routing in consumers.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDeliveryEventPublisher implements DeliveryEventPublisher {

    static final String DELIVERY_EVENTS_TOPIC = "tnt.delivery.events";
    /** Dedicated topic for mission/delivery status changes — consumed by tnt-incident-core. */
    static final String MISSION_STATUS_CHANGED_TOPIC = "tnt.delivery.mission.status-changed";
    /** Topic for FreelancerOrg assignment events (). */
    static final String FREELANCER_ORG_ASSIGNED_TOPIC = "tnt.delivery.freelancer_org.assigned";

    @Qualifier("deliveryKafkaProducer")
    private final KafkaTemplate<String, String> kafkaTemplate;
    @Qualifier("deliveryObjectMapper")
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> publish(DeliveryDomainEvent event) {
        // Route events to their dedicated topics
        String topic;
        if (event instanceof MissionStatusChangedEvent) {
            topic = MISSION_STATUS_CHANGED_TOPIC;
        } else if (event instanceof FreelancerOrgAssignedEvent) {
            topic = FREELANCER_ORG_ASSIGNED_TOPIC;
        } else {
            topic = DELIVERY_EVENTS_TOPIC;
        }
        return serializeEvent(event)
                .flatMap(payload -> Mono.fromFuture(
                        kafkaTemplate.send(topic, event.aggregateId().toString(), payload))
                        .doOnSuccess(result -> log.debug("Published event={} aggregateId={}",
                                event.getClass().getSimpleName(), event.aggregateId()))
                        .doOnError(ex -> log.error("Failed to publish event={}", event.getClass().getSimpleName(), ex))
                        .onErrorComplete()  // best-effort: notification failure must not block delivery
                        .then());
    }

    @Override
    public Mono<Void> publishAll(List<DeliveryDomainEvent> events) {
        if (events == null || events.isEmpty()) return Mono.empty();
        return Flux.fromIterable(events)
                .concatMap(this::publish)
                .then();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Mono<String> serializeEvent(DeliveryDomainEvent event) {
        try {
            String json = objectMapper.writeValueAsString(new EventEnvelope(
                    event.getClass().getSimpleName(),
                    event.aggregateId().toString(),
                    event.tenantId().toString(),
                    event.occurredAt().toString(),
                    objectMapper.valueToTree(event)));
            return Mono.just(json);
        } catch (JsonProcessingException ex) {
            log.error("Cannot serialize event {}", event.getClass().getSimpleName(), ex);
            return Mono.empty();
        }
    }

    /**
     * Envelope wrapping domain events for Kafka serialization.
     */
    record EventEnvelope(
            String eventType,
            String aggregateId,
            String tenantId,
            String occurredAt,
            com.fasterxml.jackson.databind.JsonNode payload
    ) {}
}
