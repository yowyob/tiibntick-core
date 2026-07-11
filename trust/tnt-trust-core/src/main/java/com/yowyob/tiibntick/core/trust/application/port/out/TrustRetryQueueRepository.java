package com.yowyob.tiibntick.core.trust.application.port.out;

import com.yowyob.tiibntick.core.trust.domain.model.valueobject.TrustRetryRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port — {@code TrustRetryQueueRepository}.
 *
 * <p>Persistence for {@code tnt_trust.trust_retry_queue} — the catch-up queue
 * that absorbs {@code yow.trust.events} publications made while the gateway
 * (Kafka broker or {@code yow-trust-event}) is degraded, so no
 * {@link com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent}
 * is ever lost.
 *
 * <p>Implemented by a {@code FOR UPDATE SKIP LOCKED}-based R2DBC adapter so
 * multiple {@code tnt-bootstrap} replicas can drain the queue concurrently
 * without double-publishing the same row.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface TrustRetryQueueRepository {

    /**
     * Enqueues a wire envelope for later redelivery.
     *
     * @param messageKey     the Kafka partition key (the original {@code entityId})
     * @param messagePayload the pre-serialized JSON envelope
     * @param eventType      the logistic event type name, for observability only
     * @param failureReason  why the direct publish attempt was skipped/failed
     * @return a {@link Mono} completing when the row is persisted
     */
    Mono<Void> enqueue(String messageKey, String messagePayload, String eventType, String failureReason);

    /**
     * Locks and returns up to {@code limit} pending rows, oldest first, using
     * {@code FOR UPDATE SKIP LOCKED} so concurrent replicas never grab the
     * same row. The lock is held for the duration of the enclosing
     * {@code @Transactional} boundary — callers must call {@link #markProcessed}
     * or {@link #markFailed} within that same transaction.
     *
     * @param limit the maximum batch size
     * @return a {@link Flux} of locked pending records
     */
    Flux<TrustRetryRecord> lockPendingBatch(int limit);

    /**
     * Marks a row as successfully redelivered (removes it from the pending set).
     *
     * @param retryId the row identifier
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> markProcessed(UUID retryId);

    /**
     * Records a failed redelivery attempt — increments the attempt counter and
     * stores the failure reason, leaving the row pending for the next drain tick.
     *
     * @param retryId the row identifier
     * @param reason  the failure reason
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> markFailed(UUID retryId, String reason);
}
