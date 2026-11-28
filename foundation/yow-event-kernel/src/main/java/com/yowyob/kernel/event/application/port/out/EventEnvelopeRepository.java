package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;

import java.time.Duration;
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

    // ── Outbox relay (R2: automatic re-publication) ──────────────────────────

    /**
     * Envelopes the outbox relay must (re)publish, oldest first, across all tenants:
     * <ul>
     *   <li>FAILED or RETRYING whose {@code next_attempt_at} is due (a RETRYING row is only due
     *       once its claim has expired, i.e. its relay instance died mid-attempt);</li>
     *   <li>PENDING older than {@code pendingGrace} (persisted, but the process died before the
     *       direct publish recorded its outcome).</li>
     * </ul>
     * Time is evaluated by the database ({@code NOW()}) so that application/DB clock or time-zone
     * skew cannot make a fresh envelope look stale.
     *
     * @param pendingGrace minimum age of a PENDING envelope before the relay takes it over
     * @param limit        maximum number of envelopes returned
     * @return due envelopes, oldest first
     */
    Flux<DomainEventEnvelope> findRelayCandidates(Duration pendingGrace, int limit);

    /**
     * Atomically claims an envelope for one relay attempt: status RETRYING, version
     * {@code expectedVersion + 1}, and {@code next_attempt_at = NOW() + claimTimeout} (after which
     * another instance may take it over if this one died). Optimistic: fails when the row's
     * version is no longer {@code expectedVersion}.
     *
     * @return {@code true} if this caller won the claim
     */
    Mono<Boolean> claimForRelay(EnvelopeId id, int expectedVersion, Duration claimTimeout);

    /**
     * Records a failed relay attempt: status (FAILED or DEAD), error, retry counter, the
     * already-incremented version (matched against {@code version - 1}) and the next attempt
     * time {@code NOW() + retryIn}.
     *
     * @return the updated row count (0 on an optimistic-lock conflict)
     */
    Mono<Integer> recordRelayFailure(EnvelopeId id, EnvelopeStatus status, String lastError,
                                     int retryCount, int version, Duration retryIn);

    /**
     * Counts the total number of envelopes in a given status for a tenant.
     *
     * @param status   the status to count
     * @param tenantId the owning tenant
     * @return a {@link Mono} emitting the count
     */
    Mono<Long> countByStatus(EnvelopeStatus status, String tenantId);
}
