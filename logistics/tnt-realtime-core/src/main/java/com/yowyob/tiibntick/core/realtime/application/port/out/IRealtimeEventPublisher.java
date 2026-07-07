package com.yowyob.tiibntick.core.realtime.application.port.out;

import com.yowyob.tiibntick.core.realtime.domain.event.RealtimeDomainEvent;
import reactor.core.publisher.Mono;

/**
 * Outbound port for publishing domain events from tnt-realtime-core to Kafka.
 *
 * @author MANFOUO Braun
 */
public interface IRealtimeEventPublisher {

    /**
     * Publishes a domain event to the appropriate Kafka topic.
     * The topic is determined by {@link RealtimeDomainEvent#kafkaTopic()}.
     *
     * @param event the domain event to publish
     * @return Mono completing when the event is dispatched to Kafka
     */
    Mono<Void> publish(RealtimeDomainEvent event);
}
