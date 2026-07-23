package com.yowyob.tiibntick.core.billing.invoice.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceEventPublisher;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceCancelled;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceGenerated;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoicePaid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link InvoiceEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase}/{@link PublishEventBatchUseCase} (yow-event-kernel's
 * transactional outbox) instead of sending to Kafka directly via {@code KafkaTemplate.send(...)}.
 * Envelopes are persisted in the same DB transaction as the invoice write (see the
 * {@code @Transactional} boundaries in {@code InvoiceService}), and {@code OutboxPollerService}
 * relays them to Kafka asynchronously with retry/DLQ.
 *
 * <p>The Kafka wire format is unchanged: the message value is still exactly
 * {@code objectMapper.writeValueAsString(event)} — the raw serialised domain event, with no
 * extra wrapper — so existing consumers (e.g. tnt-accounting-core) require no change. The
 * message key is still the invoice ID, now carried as {@link DomainEventEnvelope#getKafkaPartitionKey()}.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaInvoiceEventPublisher implements InvoiceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaInvoiceEventPublisher.class);
    static final String TOPIC = TntTopics.BILLING_INVOICE_EVENTS;

    private static final String AGGREGATE_TYPE = "Invoice";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final PublishEventBatchUseCase publishEventBatchUseCase;
    private final ObjectMapper objectMapper;

    public KafkaInvoiceEventPublisher(
            PublishEventUseCase publishEventUseCase,
            PublishEventBatchUseCase publishEventBatchUseCase,
            @Qualifier("invoiceObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.publishEventBatchUseCase = publishEventBatchUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(Object event, UUID tenantId) {
        return toEnvelope(event, tenantId)
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued invoice event={} key={} to outbox",
                        event.getClass().getSimpleName(), resolveKey(event)))
                .doOnError(ex -> log.error("Failed to enqueue invoice event={} to outbox",
                        event.getClass().getSimpleName(), ex));
    }

    @Override
    public Mono<Void> publishAll(List<Object> events, UUID tenantId) {
        if (events == null || events.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(events)
                .flatMap(event -> toEnvelope(event, tenantId))
                .collectList()
                .flatMap(envelopes -> envelopes.isEmpty()
                        ? Mono.<Integer>empty()
                        : publishEventBatchUseCase.publishAll(envelopes))
                .then();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Mono<DomainEventEnvelope> toEnvelope(Object event, UUID tenantId) {
        try {
            String key     = resolveKey(event);
            String payload = objectMapper.writeValueAsString(event);
            return Mono.just(DomainEventEnvelope.wrap()
                    .correlationId(UUID.randomUUID().toString())
                    .eventType(event.getClass().getSimpleName())
                    .aggregateId(key)
                    .aggregateType(AGGREGATE_TYPE)
                    .tenantId(tenantId.toString())
                    .solutionCode(SOLUTION_CODE)
                    .payload(payload)
                    .kafkaTopic(TOPIC)
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize invoice event: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private String resolveKey(Object event) {
        return switch (event) {
            case InvoiceGenerated e  -> e.invoiceId().toString();
            case InvoicePaid e       -> e.invoiceId().toString();
            case InvoiceCancelled e  -> e.invoiceId().toString();
            default -> "unknown";
        };
    }
}
