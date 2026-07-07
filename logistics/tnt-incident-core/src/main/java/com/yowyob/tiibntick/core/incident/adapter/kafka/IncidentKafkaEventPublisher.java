package com.yowyob.tiibntick.core.incident.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka implementation of IIncidentEventPublisher publishing all twelve domain events to their dedicated topics.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentKafkaEventPublisher implements IIncidentEventPublisher {

    @Qualifier("incidentKafkaTemplate")
    private final KafkaTemplate<String, String> kafkaTemplate;
    @Qualifier("incidentObjectMapper")
    private final ObjectMapper objectMapper;

    private static final String TOPIC_CREATED          = "tnt.incident.created";
    private static final String TOPIC_STATUS_CHANGED   = "tnt.incident.status.changed";
    private static final String TOPIC_TRIAGED           = "tnt.incident.triaged";
    private static final String TOPIC_DRIVER_ASSIGNED   = "tnt.incident.driver.assigned";
    private static final String TOPIC_HANDOVER_DONE     = "tnt.incident.handover.completed";
    private static final String TOPIC_RESOLVED          = "tnt.incident.resolved";
    private static final String TOPIC_CLOSED            = "tnt.incident.closed";
    private static final String TOPIC_CANCELLED         = "tnt.incident.cancelled";
    private static final String TOPIC_ESCALATED         = "tnt.incident.escalated";
    private static final String TOPIC_ESCALATED_DISPUTE = "tnt.incident.escalated.to.dispute";
    private static final String TOPIC_INTERAGENCY_REQ   = "tnt.incident.interagency.requested";
    private static final String TOPIC_INTERAGENCY_DONE  = "tnt.incident.interagency.completed";

    /**
     * Publishes an {@link com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.IncidentCreatedEvent}
     * to the {@code tnt.incident.created} Kafka topic.
     *
     * @param event the domain event to publish
     */
    /**
     * Publishes an {@link com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.IncidentResolvedEvent}
     * to the {@code tnt.incident.resolved} Kafka topic.
     *
     * @param event the domain event to publish
     */
    /**
     * Publishes an {@link com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.IncidentClosedEvent}
     * to the {@code tnt.incident.closed} Kafka topic.
     *
     * @param event the domain event to publish
     */
    /**
     * Publishes an escalation-to-dispute event to the {@code tnt.incident.escalated.to.dispute} topic.
     * This triggers automatic dispute creation in tnt-dispute-core.
     *
     * @param event the domain event to publish
     */
    @Override
    public Mono<Void> publish(IncidentCreatedEvent event) {
        return send(TOPIC_CREATED, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(IncidentStatusChangedEvent event) {
        return send(TOPIC_STATUS_CHANGED, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(IncidentTriagedEvent event) {
        return send(TOPIC_TRIAGED, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(IncidentDriverAssignedEvent event) {
        return send(TOPIC_DRIVER_ASSIGNED, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(HandoverCompletedEvent event) {
        return send(TOPIC_HANDOVER_DONE, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(IncidentResolvedEvent event) {
        return send(TOPIC_RESOLVED, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(IncidentClosedEvent event) {
        return send(TOPIC_CLOSED, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(IncidentCancelledEvent event) {
        return send(TOPIC_CANCELLED, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(IncidentEscalatedEvent event) {
        return send(TOPIC_ESCALATED, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(IncidentEscalatedToDisputeEvent event) {
        return send(TOPIC_ESCALATED_DISPUTE, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(InterAgencyCoopRequestedEvent event) {
        return send(TOPIC_INTERAGENCY_REQ, event.getIncidentId().toString(), event);
    }

    @Override
    public Mono<Void> publish(InterAgencyCoopCompletedEvent event) {
        return send(TOPIC_INTERAGENCY_DONE, event.getIncidentId().toString(), event);
    }

    private Mono<Void> send(String topic, String key, Object payload) {
        return Mono.fromCallable(() -> {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
