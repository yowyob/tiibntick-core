package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeEventPublisher;
import com.yowyob.tiibntick.core.dispute.domain.event.*;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DisputeKafkaEventPublisher implements IDisputeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DisputeKafkaEventPublisher.class);

    static final String TOPIC_OPENED = "tnt.dispute.opened";
    static final String TOPIC_STATUS_CHANGED = "tnt.dispute.status.changed";
    static final String TOPIC_EVIDENCE_SUBMITTED = "tnt.dispute.evidence.submitted";
    static final String TOPIC_RULED = "tnt.dispute.ruled";
    static final String TOPIC_ESCALATED = "tnt.dispute.escalated";
    static final String TOPIC_COMPENSATION_PROCESSED = "tnt.dispute.compensation.processed";
    static final String TOPIC_CLOSED = "tnt.dispute.closed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DisputeKafkaEventPublisher(
            @Qualifier("disputeKafkaProducerTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("disputeObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishDisputeOpened(DisputeOpened event) {
        return send(TOPIC_OPENED, event.disputeId().getValue(), event);
    }

    @Override
    public Mono<Void> publishMediatorAssigned(MediatorAssigned event) {
        return send(TOPIC_STATUS_CHANGED, event.disputeId().getValue(), event);
    }

    @Override
    public Mono<Void> publishEvidenceSubmitted(EvidenceSubmitted event) {
        return send(TOPIC_EVIDENCE_SUBMITTED, event.disputeId().getValue(), event);
    }

    @Override
    public Mono<Void> publishDisputeRuled(DisputeRuled event) {
        return send(TOPIC_RULED, event.disputeId().getValue(), event);
    }

    @Override
    public Mono<Void> publishDisputeEscalated(DisputeEscalated event) {
        return send(TOPIC_ESCALATED, event.disputeId().getValue(), event);
    }

    @Override
    public Mono<Void> publishCompensationProcessed(CompensationProcessed event) {
        return send(TOPIC_COMPENSATION_PROCESSED, event.disputeId().getValue(), event);
    }

    @Override
    public Mono<Void> publishDisputeClosed(DisputeClosed event) {
        return send(TOPIC_CLOSED, event.disputeId().getValue(), event);
    }

    @Override
    public Mono<Void> publishAll(Dispute dispute) {
        return Flux.fromIterable(dispute.getDomainEvents())
                .flatMap(event -> routeEvent(dispute, event))
                .then()
                .doOnSuccess(v -> dispute.clearDomainEvents());
    }

    // =========================================================================
    // PRIVATE
    // =========================================================================

    private Mono<Void> routeEvent(Dispute dispute, Object event) {
        return switch (event) {
            case DisputeOpened e -> publishDisputeOpened(e);
            case MediatorAssigned e -> publishMediatorAssigned(e);
            case EvidenceSubmitted e -> publishEvidenceSubmitted(e);
            case DisputeRuled e -> publishDisputeRuled(e);
            case DisputeEscalated e -> publishDisputeEscalated(e);
            case CompensationProcessed e -> publishCompensationProcessed(e);
            case DisputeClosed e -> publishDisputeClosed(e);
            default -> {
                log.warn("Unknown domain event type: {}", event.getClass().getSimpleName());
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> send(String topic, String key, Object payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .flatMap(json -> Mono.fromFuture(
                        kafkaTemplate.send(new ProducerRecord<>(topic, key, json))))
                .doOnSuccess(r -> log.debug("Published event to topic={} key={}", topic, key))
                .doOnError(e -> log.error("Failed to publish event to topic={} key={}: {}", topic, key, e.getMessage()))
                .then();
    }
}