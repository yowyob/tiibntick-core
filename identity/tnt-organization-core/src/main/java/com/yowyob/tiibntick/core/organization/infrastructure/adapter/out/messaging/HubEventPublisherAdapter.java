package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.organization.application.port.out.HubEventPublisherPort;
import com.yowyob.tiibntick.core.organization.domain.event.HubRelaisUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Outbox-backed adapter implementing {@link HubEventPublisherPort}.
 *
 * <p>Delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox)
 * instead of sending to Kafka directly, mirroring {@code FreelancerOrgEventPublisherAdapter}
 * in this same module.
 *
 * @author MANFOUO Braun
 */
public class HubEventPublisherAdapter implements HubEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(HubEventPublisherAdapter.class);

    private static final String AGGREGATE_TYPE = "HubRelais";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public HubEventPublisherAdapter(PublishEventUseCase publishEventUseCase, ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishHubUpdated(HubRelaisUpdatedEvent event) {
        return Mono.fromCallable(() -> serialize(event))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(event.eventId().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(event.hubId().toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(event.tenantId().toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(TntTopics.ORGANIZATION_HUB_UPDATED)
                        .occurredAt(LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnError(ex -> log.error("Failed to enqueue {} to outbox for topic {}: {}",
                        event.getClass().getSimpleName(), TntTopics.ORGANIZATION_HUB_UPDATED, ex.getMessage()));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event: " + event.getClass().getSimpleName(), e);
        }
    }
}
