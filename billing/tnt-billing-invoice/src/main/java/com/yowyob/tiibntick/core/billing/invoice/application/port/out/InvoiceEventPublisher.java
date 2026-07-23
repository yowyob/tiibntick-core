package com.yowyob.tiibntick.core.billing.invoice.application.port.out;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Output port: publishes invoice domain events to Kafka (via the transactional outbox —
 * see {@code KafkaInvoiceEventPublisher}, Chantier C · Audit n°3 · P5).
 *
 * <p>{@code tenantId} is accepted explicitly rather than read off the event itself:
 * not every invoice domain event (e.g. {@code InvoicePaid}, {@code InvoiceCancelled})
 * carries a {@code tenantId} field, but the outbox envelope requires one for routing/
 * isolation. Callers hold the aggregate's tenant at the point they invoke this port
 * (e.g. {@code saved.getTenantId()}).
 *
 * @author MANFOUO Braun
 */
public interface InvoiceEventPublisher {
    Mono<Void> publish(Object event, UUID tenantId);
    Mono<Void> publishAll(List<Object> events, UUID tenantId);
}
