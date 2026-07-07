package com.yowyob.kernel.event.application.port.in;

import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

/**
 * <b>Inbound port</b> — Publish a single domain event envelope to the
 * Yowyob transactional outbox pipeline.
 *
 * <p>Implementations <strong>must</strong> persist the envelope atomically
 * within the caller's active database transaction before returning. The Kafka
 * publish step happens asynchronously in a separate pass performed by the
 * outbox poller.
 *
 * <h2>Usage pattern</h2>
 * <pre>{@code
 * // Inside a domain service, called after the business operation commits:
 * DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
 *     .eventType("MissionCreatedEvent")
 *     .aggregateId(mission.getId().value())
 *     .aggregateType("Mission")
 *     .tenantId(tenantId)
 *     .solutionCode("TNT")
 *     .kafkaTopic(TntKafkaTopics.MISSIONS)
 *     .payload(jsonPayload)
 *     .correlationId(correlationId)
 *     .build();
 *
 * publishEventUseCase.publish(envelope)
 *     .subscribe();
 * }</pre>
 *
 * <p>This interface belongs to the <em>hexagonal architecture inbound ports</em>
 * layer. Callers must depend only on this interface, never on the implementation.
 */
public interface PublishEventUseCase {

    /**
     * Persists the given envelope in the transactional outbox so that the
     * poller can publish it to Kafka.
     *
     * <p>This method is idempotent when called with the same
     * {@link DomainEventEnvelope#getId()} — a duplicate envelope will be
     * silently ignored if it has already been persisted.
     *
     * @param envelope the envelope to enqueue — must not be {@code null}
     * @return a {@link Mono} that completes empty on success, or propagates
     *         an error signal if persistence fails
     */
    Mono<Void> publish(DomainEventEnvelope envelope);
}
