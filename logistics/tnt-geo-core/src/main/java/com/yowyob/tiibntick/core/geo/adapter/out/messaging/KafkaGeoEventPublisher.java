package com.yowyob.tiibntick.core.geo.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.geo.application.port.out.IGeoEventPublisher;
import com.yowyob.tiibntick.core.geo.domain.event.RoadNodeCreatedEvent;
import com.yowyob.tiibntick.core.geo.domain.event.ServiceZoneUpdatedEvent;
import com.yowyob.tiibntick.core.geo.domain.event.TrafficConditionChangedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka outbound adapter for publishing tnt-geo-core domain events.
 *
 * Topics:
 *   tnt.geo.traffic.events   — TrafficConditionChangedEvent (consumed by tnt-route-core)
 *   tnt.geo.node.events      — RoadNodeCreatedEvent (consumed by tnt-search)
 *   tnt.geo.zone.events      — ServiceZoneUpdatedEvent (consumed by tnt-actor-core, tnt-delivery-core)
 *
 * Author: MANFOUO Braun
 */
@Component
public class KafkaGeoEventPublisher implements IGeoEventPublisher {

    public static final String TOPIC_TRAFFIC    = "tnt.geo.traffic.events";
    public static final String TOPIC_NODE       = "tnt.geo.node.events";
    public static final String TOPIC_ZONE       = "tnt.geo.zone.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaGeoEventPublisher(
            @Qualifier("geoKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("geoObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishTrafficChanged(TrafficConditionChangedEvent event) {
        return send(TOPIC_TRAFFIC, event.tenantId().toString(), event);
    }

    @Override
    public Mono<Void> publishRoadNodeCreated(RoadNodeCreatedEvent event) {
        return send(TOPIC_NODE, event.tenantId().toString(), event);
    }

    @Override
    public Mono<Void> publishServiceZoneUpdated(ServiceZoneUpdatedEvent event) {
        return send(TOPIC_ZONE, event.tenantId().toString(), event);
    }

    private Mono<Void> send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
            return Mono.fromFuture(future)
                    .doOnError(ex -> logPublishError(topic, ex))
                    .then();
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }

    private void logPublishError(String topic, Throwable ex) {
        System.err.println("[KafkaGeoEventPublisher] Failed to publish to topic " + topic + ": " + ex.getMessage());
    }
}