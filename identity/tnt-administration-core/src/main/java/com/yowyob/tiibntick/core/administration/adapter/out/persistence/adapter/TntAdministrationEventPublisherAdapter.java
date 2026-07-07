package com.yowyob.tiibntick.core.administration.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.administration.application.port.out.TntAdministrationEventPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka adapter for TntAdministrationEventPublisher port.
 * Author: MANFOUO Braun
 */
@Component
public class TntAdministrationEventPublisherAdapter implements TntAdministrationEventPublisher {

    private static final String TOPIC = "tnt.administration.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TntAdministrationEventPublisherAdapter(
            @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(UUID tenantId, String eventType, String module,
                               UUID aggregateId, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("module", module);
        event.put("tenantId", tenantId.toString());
        event.put("aggregateId", aggregateId.toString());
        event.put("payload", payload);
        event.put("occurredAt", Instant.now().toString());

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(json -> Mono.fromFuture(
                        kafkaTemplate.send(TOPIC, tenantId.toString(), json).toCompletableFuture()))
                .then()
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.empty());
    }
}
