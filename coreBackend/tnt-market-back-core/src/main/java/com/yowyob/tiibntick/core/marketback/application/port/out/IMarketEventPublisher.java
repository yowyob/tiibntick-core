package com.yowyob.tiibntick.core.marketback.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port — publishes domain events to the Kafka event bus.
 * @author MANFOUO Braun
 */
public interface IMarketEventPublisher {

    /** Publishes any domain event to the appropriate Kafka topic. */
    Mono<Void> publish(Object domainEvent);

    /** Publishes a batch of domain events (pulled from an aggregate). */
    default Mono<Void> publishAll(java.util.List<Object> events) {
        return reactor.core.publisher.Flux.fromIterable(events)
                .flatMap(this::publish)
                .then();
    }
}
