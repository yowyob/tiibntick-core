package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryCompletedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryCreatedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryFailedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryInTransitEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.FreelancerOrgAssignedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.MissionStatusChangedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.ParcelAtRelayPointEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.ParcelPickedUpEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
                .map(payload -> toEnvelopes(event, payload))
                .flatMap(envelopes -> envelopes.size() == 1
                        ? publishEventUseCase.publish(envelopes.get(0))
                        : publishEventBatchUseCase.publishAll(envelopes).then())
                .doOnSuccess(v -> log.debug("Enqueued event={} aggregateId={} to outbox",
                        event.getClass().getSimpleName(), event.aggregateId()))
                .doOnError(ex -> log.error("Failed to enqueue event={} to outbox",
                        event.getClass().getSimpleName(), ex));
    }

    @Override
    public Mono<Void> publishAll(List<DeliveryDomainEvent> events) {
        if (events == null || events.isEmpty()) return Mono.empty();
        return Flux.fromIterable(events)
                .concatMap(event -> serializeEvent(event).map(payload -> toEnvelopes(event, payload)))
                .flatMapIterable(envelopes -> envelopes)
                .collectList()
                .flatMap(envelopes -> envelopes.isEmpty()
                        ? Mono.<Integer>empty()
                        : publishEventBatchUseCase.publishAll(envelopes))
                .then();
    }

    // ── Private helpers ───────────────────────────────────────────────

    /**
     * Resolves the Kafka topic(s) a domain event must be relayed to. Most events map to a
     * single topic; a few also fan out to a secondary, differently-named topic that an
     * unrelated consumer group listens on for the same real-world occurrence (Audit n°5 P-01:
     * these secondary topics used to have no producer at all).
     */
    private List<DomainEventEnvelope> toEnvelopes(DeliveryDomainEvent event, String payload) {
        List<String> topics = new ArrayList<>();
        if (event instanceof DeliveryCreatedEvent) {
            // Consumed by coreBackend's agency-assignment (property core-mission-created).
            topics.add(TntTopics.DELIVERY_MISSION_CREATED);
        } else if (event instanceof ParcelPickedUpEvent) {
            // Physical pickup = fulfillment start. Consumed by tnt-sales-core.
            topics.add(TntTopics.DELIVERY_MISSION_STARTED);
            topics.add(TntTopics.DELIVERY_PACKAGE_UPDATED);
        } else if (event instanceof DeliveryCompletedEvent) {
            // Consumed by tnt-billing-invoice, tnt-tp-core, tnt-sales-core.
            topics.add(TntTopics.DELIVERY_MISSION_COMPLETED);
            // Consumed by coreBackend's agency-assignment (property core-package-delivered).
            topics.add(TntTopics.DELIVERY_PACKAGE_DELIVERED);
        } else if (event instanceof DeliveryFailedEvent) {
            // Consumed by tnt-sales-core.
            topics.add(TntTopics.DELIVERY_MISSION_FAILED);
        } else if (event instanceof DeliveryInTransitEvent || event instanceof ParcelAtRelayPointEvent) {
            // Generic package state change. Consumed by tnt-sync-core.
            topics.add(TntTopics.DELIVERY_PACKAGE_UPDATED);
        } else if (event instanceof MissionStatusChangedEvent) {
            topics.add(MISSION_STATUS_CHANGED_TOPIC);
            topics.add(TntTopics.DELIVERY_PACKAGE_UPDATED);
        } else if (event instanceof FreelancerOrgAssignedEvent) {
            topics.add(FREELANCER_ORG_ASSIGNED_TOPIC);
        } else {
            topics.add(DELIVERY_EVENTS_TOPIC);
        }

        List<DomainEventEnvelope> envelopes = new ArrayList<>(topics.size());
        for (String topic : topics) {
            envelopes.add(toEnvelope(event, payload, topic));
        }
        return envelopes;
    }

    private DomainEventEnvelope toEnvelope(DeliveryDomainEvent event, String payload, String topic) {
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
