package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.templates.domain.event.CustomTemplateSavedEvent;
import com.yowyob.tiibntick.core.billing.templates.domain.event.TemplateAppliedEvent;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.ITemplateEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka adapter implementing {@link ITemplateEventPublisher}.
 *
 * <p>Publishes domain events as JSON messages to their respective Kafka topics.
 * The message key is the {@code ownerActorId} to ensure ordering per actor.
 *
 * <p>Kafka send operations are wrapped in a reactive {@link Mono} using
 * {@code Schedulers.boundedElastic()} to prevent blocking the event loop.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Component
public class KafkaTemplateEventPublisher implements ITemplateEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaTemplateEventPublisher(
            @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("billingTemplatesObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     * Publishes to topic: {@code tnt.billing.template.applied}
     */
    @Override
    public Mono<Void> publishTemplateApplied(TemplateAppliedEvent event) {
        return Mono.fromCallable(() -> {
                    String payload = objectMapper.writeValueAsString(event);
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                            TemplateAppliedEvent.TOPIC,
                            event.getOwnerActorId(),
                            payload
                    );
                    kafkaTemplate.send(record);
                    log.info("Published TemplateAppliedEvent: eventId={} templateCode={} policyId={}",
                            event.getEventId(), event.getTemplateCode(), event.getCreatedPolicyId());
                    return (Void) null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Failed to publish TemplateAppliedEvent for template={} actor={}",
                            event.getTemplateCode(), event.getOwnerActorId(), e);
                    // Do not fail the main flow if event publish fails
                    return Mono.empty();
                });
    }

    /**
     * {@inheritDoc}
     * Publishes to topic: {@code tnt.billing.custom_template.saved}
     */
    @Override
    public Mono<Void> publishCustomTemplateSaved(CustomTemplateSavedEvent event) {
        return Mono.fromCallable(() -> {
                    String payload = objectMapper.writeValueAsString(event);
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                            CustomTemplateSavedEvent.TOPIC,
                            event.getOwnerActorId(),
                            payload
                    );
                    kafkaTemplate.send(record);
                    log.info("Published CustomTemplateSavedEvent: eventId={} name={} actor={}",
                            event.getEventId(), event.getCustomTemplateName(), event.getOwnerActorId());
                    return (Void) null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Failed to publish CustomTemplateSavedEvent for actor={}",
                            event.getOwnerActorId(), e);
                    return Mono.empty();
                });
    }
}
