package com.yowyob.tiibntick.core.marketback.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.domain.event.MarketDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka-based implementation of {@link IMarketEventPublisher}. Serializes
 * domain events to JSON and publishes them to {@code tnt.market.<suffix>}
 * topics derived from the event's class name, using the shared
 * {@code tntKafkaTemplate}/{@code tntObjectMapper} beans provided by
 * tnt-bootstrap.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketKafkaEventPublisher implements IMarketEventPublisher {

    private static final String PREFIX = "tnt.market.";

    private final KafkaTemplate<String, String> tntKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> publish(Object event) {
        return Mono.fromCallable(() -> {
                    String topic = PREFIX + toTopicSuffix(event.getClass().getSimpleName());
                    String payload = objectMapper.writeValueAsString(event);
                    // Kafka record key = aggregate id, so tnt-sync-core's EntityChangedEventConsumer
                    // (which prefers record.key() over parsing the payload) can index the delta
                    // under the right aggregate_id instead of falling back to "unknown".
                    String key = event instanceof MarketDomainEvent marketEvent ? marketEvent.aggregateId() : null;
                    tntKafkaTemplate.send(topic, key, payload);
                    log.debug("Published event {} to topic {} (key={})", event.getClass().getSimpleName(), topic, key);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
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
