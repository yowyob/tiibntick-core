package com.yowyob.tiibntick.core.billing.invoice.application.port.out;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Output port: publishes invoice domain events to Kafka.
 *
 * @author MANFOUO Braun
 */
public interface InvoiceEventPublisher {
    Mono<Void> publish(Object event);
    Mono<Void> publishAll(List<Object> events);
}
