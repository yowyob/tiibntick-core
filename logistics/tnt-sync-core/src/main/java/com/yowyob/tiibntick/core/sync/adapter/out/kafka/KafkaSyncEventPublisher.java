package com.yowyob.tiibntick.core.sync.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.domain.event.SyncDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class KafkaSyncEventPublisher implements ISyncEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaSyncEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaSyncEventPublisher(
            @Qualifier("syncKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(SyncDomainEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(json -> Mono.fromFuture(
                        kafkaTemplate.send(event.kafkaTopic(), event.getEventId(), json)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(r -> log.debug("Published sync event {} to {}", event.getEventId(), event.kafkaTopic()))
                .doOnError(ex -> log.error("Failed to publish sync event {}: {}", event.getEventId(), ex.getMessage()))
                .onErrorResume(JsonProcessingException.class, ex -> Mono.error(
                        new RuntimeException("Failed to serialize sync event", ex)))
                .then();
    }
}
