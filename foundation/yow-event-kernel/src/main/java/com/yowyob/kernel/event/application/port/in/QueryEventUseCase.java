package com.yowyob.kernel.event.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;

import java.time.LocalDateTime;

/**
 * <b>Inbound port</b> — Query the event store for previously published
 * {@link DomainEventEnvelope}s.
 *
 * <p>The event store is append-only and never deletes committed envelopes,
 * making it suitable for audit, replay and debugging scenarios.
 *
 * <p>All query methods honour multi-tenancy: the {@code tenantId} parameter
 * acts as a mandatory isolation filter and must never be omitted.
 */
public interface QueryEventUseCase {

    /**
     * Retrieves a single envelope by its unique identifier.
     *
     * @param envelopeId the unique envelope ID
     * @param tenantId   the tenant owning the envelope
     * @return a {@link Mono} emitting the envelope, or empty if not found
     */
    Mono<DomainEventEnvelope> findById(EnvelopeId envelopeId, String tenantId);

    /**
     * Retrieves all envelopes emitted by a specific aggregate instance.
     *
     * @param aggregateId   the aggregate's unique identifier
     * @param aggregateType the aggregate type discriminator (e.g. {@code "Mission"})
     * @param tenantId      the owning tenant
     * @return a {@link Flux} of envelopes ordered by {@code occurredAt} ascending
     */
    Flux<DomainEventEnvelope> findByAggregateId(
            String aggregateId,
            String aggregateType,
            String tenantId);

    /**
     * Retrieves envelopes of a given type within a time window.
     *
     * @param eventType the fully qualified event type name
     * @param from      start of the time window (inclusive)
     * @param to        end of the time window (inclusive)
     * @param tenantId  the owning tenant
     * @param limit     maximum number of results to return (max 1000)
     * @return a {@link Flux} of matching envelopes
     */
    Flux<DomainEventEnvelope> findByEventType(
            String eventType,
            LocalDateTime from,
            LocalDateTime to,
            String tenantId,
            int limit);

    /**
     * Looks up envelopes by their business correlation ID.
     *
     * <p>Useful for tracing a chain of events triggered by a single user action
     * across multiple services.
     *
     * @param correlationId the correlation identifier
     * @param tenantId      the owning tenant
     * @return a {@link Flux} of envelopes sharing the correlation ID
     */
    Flux<DomainEventEnvelope> findByCorrelationId(String correlationId, String tenantId);

    /**
     * Verifies that an envelope with the given payload hash exists and is
     * committed, providing a lightweight integrity check.
     *
     * @param envelopeId  the envelope identifier
     * @param payloadHash the expected SHA-256 payload hash
     * @return a {@link Mono} emitting {@code true} if the hash matches
     */
    Mono<Boolean> verifyPayloadIntegrity(EnvelopeId envelopeId, String payloadHash);
}
