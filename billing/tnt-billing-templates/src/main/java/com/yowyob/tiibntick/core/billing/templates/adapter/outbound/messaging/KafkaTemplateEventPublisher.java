package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.billing.templates.domain.event.CustomTemplateSavedEvent;
import com.yowyob.tiibntick.core.billing.templates.domain.event.TemplateAppliedEvent;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.ITemplateEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Outbox-backed adapter implementing {@link ITemplateEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly via {@code KafkaTemplate.send(...)} and swallowing failures.
 * Envelopes are persisted in the same DB transaction as the business write (see the
 * {@code @Transactional} boundaries in {@code ApplyTemplateUseCase}/{@code SaveCustomTemplateUseCase}),
 * and {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ.
 *
 * <p>The Kafka wire format is unchanged: the message value is still exactly
 * {@code objectMapper.writeValueAsString(event)} — the raw serialised domain event — and the
 * message key is still the {@code ownerActorId} (ordering per actor), now carried as
 * {@link DomainEventEnvelope#getKafkaPartitionKey()}. Topics are unchanged
 * ({@code tnt.billing.template.applied} / {@code tnt.billing.custom_template.saved}), so
 * existing consumers (e.g. tnt-notify-core) require no change.
 *
 * @author MANFOUO Braun
 * @version 2.0
 * @since 2026-05-26
 */
@Slf4j
@Component
public class KafkaTemplateEventPublisher implements ITemplateEventPublisher {

    private static final String AGGREGATE_TYPE = "PolicyTemplate";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public KafkaTemplateEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("billingTemplatesObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     * Enqueues to the outbox, routed to topic {@code tnt.billing.template.applied}.
     */
    @Override
    public Mono<Void> publishTemplateApplied(TemplateAppliedEvent event) {
        return serialize(event)
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(event.getEventId().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(event.getTemplateCode())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(event.getTenantId())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(TemplateAppliedEvent.TOPIC)
                        .kafkaPartitionKey(event.getOwnerActorId())
                        .occurredAt(LocalDateTime.ofInstant(event.getOccurredAt(), ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.info(
                        "Enqueued TemplateAppliedEvent to outbox: eventId={} templateCode={} policyId={}",
                        event.getEventId(), event.getTemplateCode(), event.getCreatedPolicyId()))
                .doOnError(e -> log.error("Failed to enqueue TemplateAppliedEvent for template={} actor={}",
                        event.getTemplateCode(), event.getOwnerActorId(), e));
    }

    /**
     * {@inheritDoc}
     * Enqueues to the outbox, routed to topic {@code tnt.billing.custom_template.saved}.
     */
    @Override
    public Mono<Void> publishCustomTemplateSaved(CustomTemplateSavedEvent event) {
        return serialize(event)
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(event.getEventId().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(event.getCustomTemplateId().toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(event.getTenantId())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(CustomTemplateSavedEvent.TOPIC)
                        .kafkaPartitionKey(event.getOwnerActorId())
                        .occurredAt(LocalDateTime.ofInstant(event.getOccurredAt(), ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.info(
                        "Enqueued CustomTemplateSavedEvent to outbox: eventId={} name={} actor={}",
                        event.getEventId(), event.getCustomTemplateName(), event.getOwnerActorId()))
                .doOnError(e -> log.error("Failed to enqueue CustomTemplateSavedEvent for actor={}",
                        event.getOwnerActorId(), e));
    }

    // ─── Private helpers ───────────────────────────────────────────────────

    private Mono<String> serialize(Object event) {
        try {
            return Mono.just(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("Cannot serialize event {}", event.getClass().getSimpleName(), e);
            return Mono.error(e);
        }
    }
}
