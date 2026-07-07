package com.yowyob.tiibntick.core.realtime.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.domain.event.RealtimeDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka adapter implementing {@link IRealtimeEventPublisher}.
 * Publishes all realtime domain events to their respective Kafka topics.
 *
 * <p>Each event's {@link RealtimeDomainEvent#kafkaTopic()} determines
 * the destination topic. The event ID is used as the Kafka message key
 * for ordering guarantees within a partition.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaRealtimeEventPublisher implements IRealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaRealtimeEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaRealtimeEventPublisher(
            @Qualifier("realtimeKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(RealtimeDomainEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(json -> Mono.fromFuture(kafkaTemplate.send(event.kafkaTopic(), event.getEventId(), json)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> log.debug("Published event {} to topic {}",
                        event.getEventId(), event.kafkaTopic()))
                .doOnError(ex -> log.error("Failed to publish event {} to topic {}: {}",
                        event.getEventId(), event.kafkaTopic(), ex.getMessage()))
                .onErrorResume(JsonProcessingException.class, ex -> Mono.error(
                        new RuntimeException("Failed to serialize event: " + event.getEventId(), ex)))
                .then();
    }
}
