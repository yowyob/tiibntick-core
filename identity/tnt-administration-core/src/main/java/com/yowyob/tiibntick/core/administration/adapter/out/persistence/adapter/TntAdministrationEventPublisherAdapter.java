package com.yowyob.tiibntick.core.administration.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.administration.application.port.out.TntAdministrationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox-backed adapter for the TntAdministrationEventPublisher port.
 *
 * <p>Chantier C · Audit n°3 · P5 (identity domain): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of a
 * direct {@code KafkaTemplate.send()} — the previous {@code onErrorResume(Mono.empty())}
 * silently dropped events on any failure. The Kafka message body keeps the exact same
 * {@code {eventType, module, tenantId, aggregateId, payload, occurredAt}} JSON shape,
 * so existing consumers of {@code tnt.administration.events} are unaffected.
 *
 * @author MANFOUO Braun
 */
@Component
public class TntAdministrationEventPublisherAdapter implements TntAdministrationEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(TntAdministrationEventPublisherAdapter.class);

    private static final String TOPIC = "tnt.administration.events";
    private static final String AGGREGATE_TYPE = "Administration";
    private static final String FREELANCER_ORG_AGGREGATE_TYPE = "FreelancerOrganization";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public TntAdministrationEventPublisherAdapter(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(UUID tenantId, String eventType, String module,
                               UUID aggregateId, Map<String, Object> payload) {
        Instant occurredAt = Instant.now();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("module", module);
        event.put("tenantId", tenantId.toString());
        event.put("aggregateId", aggregateId.toString());
        event.put("payload", payload);
        event.put("occurredAt", occurredAt.toString());

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(eventType)
                        .aggregateId(aggregateId.toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(TOPIC)
                        // Preserve the pre-migration partitioning contract: the Kafka key was
                        // tenantId (per-tenant ordering), not the default aggregateId.
                        .kafkaPartitionKey(tenantId.toString())
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnError(e -> log.error("Failed to enqueue administration event {} to outbox: {}",
                        eventType, e.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Backs the FreelancerOrg admin lifecycle events (KYC approval/rejection, suspension,
     * blacklist) — {@code topic} is one of the dedicated {@code tnt.admin.freelancer_org.*}
     * constants, keyed by tenant, aggregate id taken from the payload's {@code orgId} field.
     */
    @Override
    public Mono<Void> publish(String topic, UUID tenantId, Map<String, Object> payload) {
        Instant occurredAt = Instant.now();
        Object orgId = payload.get("orgId");
        String aggregateId = orgId != null ? orgId.toString() : tenantId.toString();

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(topic)
                        .aggregateId(aggregateId)
                        .aggregateType(FREELANCER_ORG_AGGREGATE_TYPE)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(topic)
                        .kafkaPartitionKey(aggregateId)
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnError(e -> log.error("Failed to enqueue FreelancerOrg admin event on topic {}: {}",
                        topic, e.getMessage()));
    }
}
