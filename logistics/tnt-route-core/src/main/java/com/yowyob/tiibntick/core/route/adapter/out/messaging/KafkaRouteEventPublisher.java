package com.yowyob.tiibntick.core.route.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.route.application.port.out.IRouteEventPublisher;
import com.yowyob.tiibntick.core.route.domain.event.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class KafkaRouteEventPublisher implements IRouteEventPublisher {

    public static final String TOPIC_TOUR    = "tnt.route.tour.events";
    public static final String TOPIC_ETA     = "tnt.route.eta.events";
    public static final String TOPIC_REROUTE = "tnt.route.reroute.events";
    public static final String TOPIC_VRP     = "tnt.route.vrp.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaRouteEventPublisher(
            @Qualifier("routeKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("routeObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishTourOptimized(TourOptimizedEvent e) {
        return send(TOPIC_TOUR, e.tenantId().toString(), e);
    }

    @Override
    public Mono<Void> publishEtaUpdated(EtaUpdatedEvent e) {
        return send(TOPIC_ETA, e.missionId(), e);
    }

    @Override
    public Mono<Void> publishReroutingTriggered(ReroutingTriggeredEvent e) {
        return send(TOPIC_REROUTE, e.missionId(), e);
    }

    @Override
    public Mono<Void> publishVrpFallback(VrpFallbackActivatedEvent e) {
        return send(TOPIC_VRP, e.tenantId().toString(), e);
    }

    private Mono<Void> send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return Mono.fromFuture(kafkaTemplate.send(new ProducerRecord<>(topic, key, json))).then();
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }
}