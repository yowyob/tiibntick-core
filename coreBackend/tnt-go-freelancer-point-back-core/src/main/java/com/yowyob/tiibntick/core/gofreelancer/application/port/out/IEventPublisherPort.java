package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outgoing port — publishes domain events to Kafka.
 * Implemented by the Kafka adapter in infrastructure.
 */
public interface IEventPublisherPort {

    Mono<Void> publish(String topic, Object payload);
}
