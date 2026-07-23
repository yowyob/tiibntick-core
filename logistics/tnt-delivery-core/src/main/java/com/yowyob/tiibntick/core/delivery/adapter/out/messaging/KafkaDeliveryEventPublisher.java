package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.FreelancerOrgAssignedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.MissionStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Outbox-backed adapter implementing {@link DeliveryEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 pilot migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase}/{@link PublishEventBatchUseCase} (yow-event-kernel's
 * transactional outbox) instead of sending to Kafka directly. Envelopes are persisted
 * in the same DB transaction as the business write (see the {@code @Transactional}
 * boundaries in {@code DeliveryLifecycleService}/{@code DeliveryAnnouncementService}),
 * and {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ —
 * a business save can no longer succeed while its event is silently lost.
 *
 * <p>The Kafka wire format is unchanged: {@link #serializeEvent} still produces the
 * exact same {@code {eventType, aggregateId, tenantId, occurredAt, payload}} envelope
 * JSON as the message body (only the envelope's own routing metadata — topic, tenant,
 * correlation id — is now carried by {@link DomainEventEnvelope}/Kafka headers instead
 * of by direct {@code KafkaTemplate} arguments), so existing consumers
 * (tnt-incident-core, tnt-sync-core, tnt-realtime-core, tnt-market-back-core) require
 * no change.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class KafkaDeliveryEventPublisher implements DeliveryEventPublisher {

    static final String DELIVERY_EVENTS_TOPIC = TntTopics.DELIVERY_EVENTS;
    /** Dedicated topic for mission/delivery status changes — consumed by tnt-incident-core. */
    static final String MISSION_STATUS_CHANGED_TOPIC = TntTopics.DELIVERY_MISSION_STATUS_CHANGED;
    /** Topic for FreelancerOrg assignment events. */
    static final String FREELANCER_ORG_ASSIGNED_TOPIC = TntTopics.DELIVERY_FREELANCER_ORG_ASSIGNED;

    private static final String AGGREGATE_TYPE = "Delivery";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final PublishEventBatchUseCase publishEventBatchUseCase;
    private final ObjectMapper objectMapper;

    public KafkaDeliveryEventPublisher(
            PublishEventUseCase publishEventUseCase,
            PublishEventBatchUseCase publishEventBatchUseCase,
            @Qualifier("deliveryObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.publishEventBatchUseCase = publishEventBatchUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(DeliveryDomainEvent event) {
        return serializeEvent(event)
                .map(payload -> toEnvelope(event, payload))
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event={} aggregateId={} to outbox",
                        event.getClass().getSimpleName(), event.aggregateId()))
                .doOnError(ex -> log.error("Failed to enqueue event={} to outbox",
                        event.getClass().getSimpleName(), ex));
    }

    @Override
    public Mono<Void> publishAll(List<DeliveryDomainEvent> events) {
        if (events == null || events.isEmpty()) return Mono.empty();
        return Flux.fromIterable(events)
                .flatMap(event -> serializeEvent(event).map(payload -> toEnvelope(event, payload)))
                .collectList()
                .flatMap(envelopes -> envelopes.isEmpty()
                        ? Mono.<Integer>empty()
                        : publishEventBatchUseCase.publishAll(envelopes))
                .then();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private DomainEventEnvelope toEnvelope(DeliveryDomainEvent event, String payload) {
        String topic;
        if (event instanceof MissionStatusChangedEvent) {
            topic = MISSION_STATUS_CHANGED_TOPIC;
        } else if (event instanceof FreelancerOrgAssignedEvent) {
            topic = FREELANCER_ORG_ASSIGNED_TOPIC;
        } else {
            topic = DELIVERY_EVENTS_TOPIC;
        }

        return DomainEventEnvelope.wrap()
                .correlationId(event.eventId().toString())
                .eventType(event.getClass().getSimpleName())
                .aggregateId(event.aggregateId().toString())
                .aggregateType(AGGREGATE_TYPE)
                .tenantId(event.tenantId().toString())
                .solutionCode(SOLUTION_CODE)
                .payload(payload)
                .kafkaTopic(topic)
                .occurredAt(LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .build();
    }

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
     * Envelope wrapping domain events for Kafka serialization (message body — distinct
     * from {@link DomainEventEnvelope}, which is the outbox's own routing envelope).
     */
    record EventEnvelope(
            String eventType,
            String aggregateId,
            String tenantId,
            String occurredAt,
            com.fasterxml.jackson.databind.JsonNode payload
    ) {}
}
