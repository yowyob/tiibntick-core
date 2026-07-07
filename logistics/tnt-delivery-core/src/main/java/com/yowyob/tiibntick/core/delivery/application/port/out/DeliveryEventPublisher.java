package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Outbound port for publishing delivery domain events to the event bus (Kafka).
 *
 * <p>Implementation uses the transactional outbox pattern to ensure at-least-once delivery:
 * events are first written to an outbox table inside the same transaction as the aggregate,
 * then relayed to Kafka by a separate relay process.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryEventPublisher {

    /**
     * Publishes a single domain event.
     */
    Mono<Void> publish(DeliveryDomainEvent event);

    /**
     * Publishes all collected domain events from an aggregate in order.
     */
    Mono<Void> publishAll(List<DeliveryDomainEvent> events);
}
