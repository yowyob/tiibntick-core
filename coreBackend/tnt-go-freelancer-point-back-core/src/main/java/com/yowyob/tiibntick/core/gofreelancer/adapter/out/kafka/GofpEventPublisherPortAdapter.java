package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IEventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbound adapter implementing IEventPublisherPort via yow-event-kernel outbox.
 * Replaces the raw KafkaTemplate approach — events are persisted transactionally
 * before being forwarded to Kafka by the outbox poller.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Component
public class GofpEventPublisherPortAdapter implements IEventPublisherPort {

    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public GofpEventPublisherPortAdapter(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(String topic, Object payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(payload.getClass().getSimpleName())
                        .aggregateId(UUID.randomUUID().toString())
                        .aggregateType("GofpEvent")
                        .tenantId(SOLUTION_CODE)
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(topic)
                        .occurredAt(LocalDateTime.now())
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnError(e -> log.error("Failed to publish event to topic {}: {}", topic, e.getMessage()));
    }
}
