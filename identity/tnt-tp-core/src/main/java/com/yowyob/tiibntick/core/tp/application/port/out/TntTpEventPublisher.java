package com.yowyob.tiibntick.core.tp.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Output port: publishes tnt-tp-core domain events to the event bus (Kafka).
 *
 * @author MANFOUO Braun
 */
public interface TntTpEventPublisher {

    /**
     * Publishes a domain event to the appropriate Kafka topic.
     *
     * @param event the domain event to publish
     * @return empty Mono on success
     */
    Mono<Void> publish(Object event);

    /**
     * Publishes a list of domain events (typically after aggregate persistence).
     *
     * @param events the list of domain events
     * @return empty Mono on success
     */
    Mono<Void> publishAll(java.util.List<Object> events);
}
