package com.yowyob.tiibntick.core.sales.application.port.out;

import com.yowyob.tiibntick.core.sales.domain.event.*;
import reactor.core.publisher.Mono;


/**
 * Outbound port for publishing sales domain events to Kafka.
 * Author: MANFOUO Braun
 */
public interface SalesEventPublisher {
    Mono<Void> publishOrderConfirmed(SalesOrderConfirmedEvent event);
    Mono<Void> publishOrderDispatched(SalesOrderDispatchedEvent event);
    Mono<Void> publishOrderDelivered(SalesOrderDeliveredEvent event);
    Mono<Void> publishOrderCancelled(SalesOrderCancelledEvent event);
}
