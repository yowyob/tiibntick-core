package com.yowyob.tiibntick.core.marketback.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port — publishes domain events to the Kafka event bus.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): {@code tenantId} was added as an
 * explicit parameter here rather than pulled from the domain event itself, because several
 * Market domain events (e.g. {@code MarketListingApprovedEvent}, {@code MerchantContractSignedEvent})
 * don't carry a {@code tenantId} field — the transactional outbox's envelope requires one, but the
 * Kafka wire payload must stay byte-for-byte identical to the pre-migration
 * {@code objectMapper.writeValueAsString(event)} body, so it cannot be added to the event records
 * themselves. Every call site already has {@code tenantId} in scope (it's always either a command
 * field or a method parameter), so this is metadata-only — the outbox envelope's own routing
 * information, not part of the published payload.
 *
 * @author MANFOUO Braun
 */
public interface IMarketEventPublisher {

    /** Publishes any domain event to the appropriate Kafka topic. */
    Mono<Void> publish(Object domainEvent, String tenantId);

    /** Publishes a batch of domain events (pulled from an aggregate). */
    default Mono<Void> publishAll(java.util.List<Object> events, String tenantId) {
        return reactor.core.publisher.Flux.fromIterable(events)
                .flatMap(event -> publish(event, tenantId))
                .then();
    }
}
