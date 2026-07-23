package com.yowyob.tiibntick.core.marketback.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.domain.event.MarketDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbox-backed implementation of {@link IMarketEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * {@code tntKafkaTemplate.send(...)} inside a fire-and-forget {@code Mono.fromCallable}.
 * Envelopes are persisted in the same DB transaction as the business write (see the
 * {@code @Transactional} boundaries added to the calling application services), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ.
 *
 * <p>The Kafka wire format is unchanged: {@code objectMapper.writeValueAsString(event)} is still
 * the exact message body (no envelope wrapper), same {@code tnt.market.<suffix>} topic derived
 * from the event's class name, same record key (the event's {@link MarketDomainEvent#aggregateId()}
 * when it implements the marker interface) — so {@code tnt-sync-core}'s
 * {@code EntityChangedEventConsumer} and any other existing consumer require zero changes.
 *
 * <p>{@code tenantId} is now an explicit parameter on {@link IMarketEventPublisher} (see its
 * Javadoc): several Market events don't carry a tenant id themselves, but the outbox envelope
 * requires one — every call site already has it in scope.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketKafkaEventPublisher implements IMarketEventPublisher {

    private static final String PREFIX = "tnt.market.";
    private static final String SOLUTION_CODE = "TNT";
    private static final String DEFAULT_AGGREGATE_TYPE = "Market";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> publish(Object event, String tenantId) {
        return Mono.fromCallable(() -> toEnvelope(event, tenantId))
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event {} to outbox (topic={})",
                        event.getClass().getSimpleName(),
                        PREFIX + toTopicSuffix(event.getClass().getSimpleName())))
                .doOnError(e -> log.error("Failed to enqueue event {} to outbox: {}",
                        event.getClass().getSimpleName(), e.getMessage()));
    }

    // ── Private helpers ───────────────────────────────────────────────

    private DomainEventEnvelope toEnvelope(Object event, String tenantId) throws Exception {
        String topic = PREFIX + toTopicSuffix(event.getClass().getSimpleName());
        // Kafka record key = aggregate id, so tnt-sync-core's EntityChangedEventConsumer
        // (which prefers record.key() over parsing the payload) can index the delta
        // under the right aggregate_id instead of falling back to "unknown".
        String aggregateId = event instanceof MarketDomainEvent marketEvent
                ? marketEvent.aggregateId()
                : UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(event);

        return DomainEventEnvelope.wrap()
                .correlationId(UUID.randomUUID().toString())
                .eventType(event.getClass().getSimpleName())
                .aggregateId(aggregateId)
                .aggregateType(DEFAULT_AGGREGATE_TYPE)
                .tenantId(tenantId)
                .solutionCode(SOLUTION_CODE)
                .payload(payload)
                .kafkaTopic(topic)
                .kafkaPartitionKey(aggregateId)
                .build();
    }

    private String toTopicSuffix(String className) {
        // e.g. MarketListingPublishedEvent -> listing.published
        return className
                .replace("Event", "")
                .replaceAll("([A-Z])", "-$1")
                .toLowerCase()
                .replaceFirst("^-", "")
                .replace("market-", "")
                .replace("-", ".");
    }
}
