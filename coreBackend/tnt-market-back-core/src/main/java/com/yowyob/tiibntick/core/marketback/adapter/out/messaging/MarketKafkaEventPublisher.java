package com.yowyob.tiibntick.core.marketback.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.domain.event.MarketDomainEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;
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

    /**
     * Topic of each Market event (lot L8, spec annex 9.1: the topic was built by concatenating "tnt.market." with the
     * class name; it is now the {@code TntTopics} constant, declared as a {@code NewTopic}). Same names as before.
     */
    static final java.util.Map<Class<?>, String> TOPICS = java.util.Map.of(
            com.yowyob.tiibntick.core.marketback.domain.event.MarketListingPublishedEvent.class, TntTopics.MARKET_LISTING_PUBLISHED,
            com.yowyob.tiibntick.core.marketback.domain.event.MarketListingApprovedEvent.class, TntTopics.MARKET_LISTING_APPROVED,
            com.yowyob.tiibntick.core.marketback.domain.event.MarketListingRejectedEvent.class, TntTopics.MARKET_LISTING_REJECTED,
            com.yowyob.tiibntick.core.marketback.domain.event.MerchantContractSignedEvent.class, TntTopics.MARKET_MERCHANT_CONTRACT_SIGNED,
            com.yowyob.tiibntick.core.marketback.domain.event.MarketOrderCreatedEvent.class, TntTopics.MARKET_ORDER_CREATED,
            com.yowyob.tiibntick.core.marketback.domain.event.MarketOrderCompletedEvent.class, TntTopics.MARKET_ORDER_COMPLETED,
            com.yowyob.tiibntick.core.marketback.domain.event.MarketOrderPaidEvent.class, TntTopics.MARKET_ORDER_PAID,
            com.yowyob.tiibntick.core.marketback.domain.event.ProviderReviewPublishedEvent.class, TntTopics.MARKET_PROVIDER_REVIEW_PUBLISHED,
            com.yowyob.tiibntick.core.marketback.domain.event.QuoteRequestCreatedEvent.class, TntTopics.MARKET_QUOTE_REQUEST_CREATED,
            com.yowyob.tiibntick.core.marketback.domain.event.QuoteResponseSubmittedEvent.class, TntTopics.MARKET_QUOTE_RESPONSE_SUBMITTED);
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
                        topicOf(event)))
                .doOnError(e -> log.error("Failed to enqueue event {} to outbox: {}",
                        event.getClass().getSimpleName(), e.getMessage()));
    }

    // ── Private helpers ───────────────────────────────────────────────

    private DomainEventEnvelope toEnvelope(Object event, String tenantId) throws Exception {
        String topic = topicOf(event);
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

    static String topicOf(Object event) {
        String topic = TOPICS.get(event.getClass());
        if (topic == null) {
            throw new IllegalArgumentException("No Market topic for " + event.getClass().getName());
        }
        return topic;
    }
}
