package com.yowyob.tiibntick.core.trust.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.TrustRetryRecord;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustRetryQueueRepository;

import java.time.LocalDateTime;
import java.util.UUID;

// ============================================================
// R2DBC Entity
// ============================================================

/**
 * R2DBC Entity — {@code TrustRetryQueueEntity}.
 *
 * <p>Maps to the {@code tnt_trust.trust_retry_queue} table.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_trust", name = "trust_retry_queue")
class TrustRetryQueueEntity {

    @Id
    @Column("retry_id")
    private UUID retryId;

    @Column("message_key")
    private String messageKey;

    @Column("message_payload")
    private String messagePayload;

    @Column("event_type")
    private String eventType;

    @Column("attempt_count")
    private int attemptCount;

    @Column("failure_reason")
    private String failureReason;

    @Column("created_at")
    private LocalDateTime createdAt;

    TrustRetryQueueEntity() {
    }

    static TrustRetryQueueEntity create(
            final String messageKey, final String messagePayload,
            final String eventType, final String failureReason) {
        final TrustRetryQueueEntity entity = new TrustRetryQueueEntity();
        entity.messageKey = messageKey;
        entity.messagePayload = messagePayload;
        entity.eventType = eventType;
        entity.attemptCount = 0;
        entity.failureReason = failureReason;
        entity.createdAt = LocalDateTime.now();
        return entity;
    }

    TrustRetryRecord toDomain() {
        return new TrustRetryRecord(retryId, messageKey, messagePayload, eventType, attemptCount, createdAt);
    }

    // Getters & setters for R2DBC
    public UUID getRetryId() { return retryId; }
    public void setRetryId(final UUID v) { this.retryId = v; }
    public String getMessageKey() { return messageKey; }
    public void setMessageKey(final String v) { this.messageKey = v; }
    public String getMessagePayload() { return messagePayload; }
    public void setMessagePayload(final String v) { this.messagePayload = v; }
    public String getEventType() { return eventType; }
    public void setEventType(final String v) { this.eventType = v; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(final int v) { this.attemptCount = v; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(final String v) { this.failureReason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(final LocalDateTime v) { this.createdAt = v; }
}

// ============================================================
// Spring Data R2DBC Repository
// ============================================================

/**
 * Spring Data reactive repository for the trust retry queue.
 * Not exposed directly — wrapped by {@link TrustRetryQueueRepositoryAdapter}.
 *
 * @author MANFOUO Braun
 */
@Repository
interface TrustRetryQueueR2dbcRepository extends ReactiveCrudRepository<TrustRetryQueueEntity, UUID> {

    /**
     * Locks and returns up to {@code limit} pending rows, oldest first.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} lets multiple {@code tnt-bootstrap}
     * replicas drain the queue concurrently without double-publishing the same
     * row — each replica only ever sees the rows the others haven't already
     * locked. The lock is held for the duration of the enclosing
     * {@code @Transactional} boundary (see {@code TrustRetryQueueDrainer}) —
     * {@code markProcessed}/{@code markFailed} MUST run in that same
     * transaction, otherwise this provides no protection against double-drain.
     */
    @Query("""
            SELECT * FROM tnt_trust.trust_retry_queue
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
    Flux<TrustRetryQueueEntity> lockPending(int limit);

    @Modifying
    @Query("""
            UPDATE tnt_trust.trust_retry_queue
            SET attempt_count = attempt_count + 1,
                failure_reason = :reason
            WHERE retry_id = :retryId
            """)
    Mono<Void> incrementFailure(UUID retryId, String reason);
}

// ============================================================
// Persistence Adapter (Anti-Corruption Layer)
// ============================================================

/**
 * Persistence Adapter — {@code TrustRetryQueueRepositoryAdapter}.
 *
 * <p>Implements {@link TrustRetryQueueRepository} by delegating to
 * {@link TrustRetryQueueR2dbcRepository}.
 *
 * @author MANFOUO Braun
 */
@Component
public class TrustRetryQueueRepositoryAdapter implements TrustRetryQueueRepository {

    private final TrustRetryQueueR2dbcRepository r2dbcRepository;

    public TrustRetryQueueRepositoryAdapter(final TrustRetryQueueR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> enqueue(
            final String messageKey, final String messagePayload,
            final String eventType, final String failureReason) {
        return r2dbcRepository.save(
                TrustRetryQueueEntity.create(messageKey, messagePayload, eventType, failureReason)).then();
    }

    /** {@inheritDoc} */
    @Override
    public Flux<TrustRetryRecord> lockPendingBatch(final int limit) {
        return r2dbcRepository.lockPending(limit).map(TrustRetryQueueEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> markProcessed(final UUID retryId) {
        return r2dbcRepository.deleteById(retryId);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> markFailed(final UUID retryId, final String reason) {
        return r2dbcRepository.incrementFailure(retryId, reason);
    }
}
