package com.yowyob.tiibntick.core.sales.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.sales.application.port.out.SalesEventPublisher;
import com.yowyob.tiibntick.core.sales.domain.event.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka adapter implementing SalesEventPublisher port.
 * Author: MANFOUO Braun
 */
@Component
public class SalesEventPublisherAdapter implements SalesEventPublisher {

    private static final String TOPIC_CONFIRMED  = "tnt.sales.order.confirmed";
    private static final String TOPIC_DISPATCHED = "tnt.sales.order.dispatched";
    private static final String TOPIC_DELIVERED  = "tnt.sales.order.delivered";
    private static final String TOPIC_CANCELLED  = "tnt.sales.order.cancelled";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SalesEventPublisherAdapter(
            @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishOrderConfirmed(SalesOrderConfirmedEvent event) {
        return publish(TOPIC_CONFIRMED, event.orderId().toString(), event);
    }

    @Override
    public Mono<Void> publishOrderDispatched(SalesOrderDispatchedEvent event) {
        return publish(TOPIC_DISPATCHED, event.orderId().toString(), event);
    }

    @Override
    public Mono<Void> publishOrderDelivered(SalesOrderDeliveredEvent event) {
        return publish(TOPIC_DELIVERED, event.orderId().toString(), event);
    }

    @Override
    public Mono<Void> publishOrderCancelled(SalesOrderCancelledEvent event) {
        return publish(TOPIC_CANCELLED, event.orderId().toString(), event);
    }

    private Mono<Void> publish(String topic, String key, Object payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .flatMap(json -> Mono.fromFuture(kafkaTemplate.send(topic, key, json).toCompletableFuture()))
                .then()
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.empty());
    }
}
