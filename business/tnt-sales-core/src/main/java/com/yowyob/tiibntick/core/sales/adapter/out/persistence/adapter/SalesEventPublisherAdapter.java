package com.yowyob.tiibntick.core.sales.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.sales.application.port.out.SalesEventPublisher;
import com.yowyob.tiibntick.core.sales.domain.event.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link SalesEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly and swallowing send failures. Envelopes are persisted in
 * the same DB transaction as the business write (see the {@code @Transactional}
 * boundaries on {@code SalesApplicationService}'s confirm/dispatch/deliver/cancel
 * use cases), and {@code OutboxPollerService} relays them to Kafka asynchronously
 * with retry/DLQ — a business save can no longer succeed while its event is
 * silently lost.
 *
 * <p>The Kafka wire format is unchanged: the payload is still simply
 * {@code objectMapper.writeValueAsString(event)} — the raw event record serialized
 * directly, with no extra wrapper envelope — so existing consumers require no change.
 *
 * @author MANFOUO Braun
 */
@Component
public class SalesEventPublisherAdapter implements SalesEventPublisher {

    static final String TOPIC_CONFIRMED  = "tnt.sales.order.confirmed";
    static final String TOPIC_DISPATCHED = "tnt.sales.order.dispatched";
    static final String TOPIC_DELIVERED  = "tnt.sales.order.delivered";
    static final String TOPIC_CANCELLED  = "tnt.sales.order.cancelled";

    private static final String AGGREGATE_TYPE = "SalesOrder";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public SalesEventPublisherAdapter(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishOrderConfirmed(SalesOrderConfirmedEvent event) {
        return enqueue(TOPIC_CONFIRMED, event, event.orderId(), event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publishOrderDispatched(SalesOrderDispatchedEvent event) {
        return enqueue(TOPIC_DISPATCHED, event, event.orderId(), event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publishOrderDelivered(SalesOrderDeliveredEvent event) {
        return enqueue(TOPIC_DELIVERED, event, event.orderId(), event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publishOrderCancelled(SalesOrderCancelledEvent event) {
        return enqueue(TOPIC_CANCELLED, event, event.orderId(), event.tenantId(), event.occurredAt());
    }

    private Mono<Void> enqueue(String topic, Object event, UUID orderId, UUID tenantId, Instant occurredAt) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(orderId.toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(topic)
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish);
    }
}
