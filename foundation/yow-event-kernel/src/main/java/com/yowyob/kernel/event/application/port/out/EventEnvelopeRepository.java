package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;

import java.time.LocalDateTime;

/**
 * <b>Outbound port</b> — Persistent storage for {@link DomainEventEnvelope}s.
 *
 * <p>The event store is the append-only source of truth for the Yowyob event
 * bus. Implementations must target the {@code yow_kernel_db} database in the
 * schema {@code trust_events_log} (for audit) and {@code outbox_entries}.
 *
 * <p>All operations are reactive (non-blocking) and honour multi-tenancy via
 * the {@code tenant_id} column coupled with Row-Level Security in PostgreSQL.
 */
public interface EventEnvelopeRepository {

    /**
     * Persists a new envelope. Called within the business transaction.
     *
     * @param envelope the envelope to persist — must have status PENDING
     * @return a {@link Mono} emitting the persisted envelope
     */
    Mono<DomainEventEnvelope> save(DomainEventEnvelope envelope);

    /**
     * Saves multiple envelopes atomically (batch insert).
     *
     * @param envelopes the envelopes to persist
     * @return a {@link Mono} emitting the count of inserted rows
     */
    Mono<Integer> saveAll(Iterable<DomainEventEnvelope> envelopes);

    /**
     * Retrieves an envelope by its identifier within the given tenant scope.
     *
     * @param id       the envelope identifier
     * @param tenantId the owning tenant
     * @return a {@link Mono} emitting the envelope, or empty if not found
     */
    Mono<DomainEventEnvelope> findById(EnvelopeId id, String tenantId);

    /**
     * Retrieves all envelopes for a given aggregate, ordered chronologically.
     *
     * @param aggregateId   the aggregate identifier
     * @param aggregateType the aggregate type discriminator
     * @param tenantId      the owning tenant
     * @return a {@link Flux} of envelopes in ascending {@code occurred_at} order
     */
    Flux<DomainEventEnvelope> findByAggregateId(
            String aggregateId, String aggregateType, String tenantId);

    /**
     * Retrieves envelopes of a specific event type within a time window.
     *
     * @param eventType the event type name
     * @param from      inclusive window start
     * @param to        inclusive window end
     * @param tenantId  the owning tenant
     * @param limit     maximum results (bounded at repository level to prevent OOM)
     * @return a {@link Flux} of matching envelopes
     */
    Flux<DomainEventEnvelope> findByEventType(
            String eventType, LocalDateTime from, LocalDateTime to, String tenantId, int limit);

    /**
     * Retrieves envelopes sharing a correlation ID.
     *
     * @param correlationId the business correlation identifier
     * @param tenantId      the owning tenant
     * @return a {@link Flux} of correlated envelopes
     */
    Flux<DomainEventEnvelope> findByCorrelationId(String correlationId, String tenantId);

    /**
     * Updates the status and metadata of an existing envelope.
     * Used by the outbox poller after each publish attempt.
     *
     * @param id          the envelope to update
     * @param status      the new status
     * @param publishedAt publication timestamp (may be {@code null} for non-PUBLISHED states)
     * @param lastError   last error message (may be {@code null} for successful transitions)
     * @param retryCount  updated retry counter
     * @param version     current optimistic-lock version (incremented by the domain object)
     * @return a {@link Mono} emitting the updated row count (0 if optimistic lock conflict)
     */
    Mono<Integer> updateStatus(
            EnvelopeId id,
            EnvelopeStatus status,
            LocalDateTime publishedAt,
            String lastError,
            int retryCount,
            int version);

    /**
     * Counts the total number of envelopes in a given status for a tenant.
     *
     * @param status   the status to count
     * @param tenantId the owning tenant
     * @return a {@link Mono} emitting the count
     */
    Mono<Long> countByStatus(EnvelopeStatus status, String tenantId);
}
