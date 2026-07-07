package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * <b>Outbound port</b> — Redis-backed idempotency store for event processing.
 *
 * <p>Prevents duplicate processing of the same event when a consumer restarts
 * mid-batch or when the outbox poller publishes a message more than once due
 * to an at-least-once delivery guarantee.
 *
 * <p>Each processed event is recorded by its {@code correlationId} with a
 * configurable TTL. If the same {@code correlationId} appears before the TTL
 * expires, the event is considered a duplicate and skipped.
 *
 * <p>The implementation uses a Redis {@code SET NX EX} (set-if-not-exists with
 * expiry) to ensure atomic idempotency checks.
 */
public interface EventIdempotencyStorePort {

    /**
     * Checks whether the given correlation ID has already been processed.
     *
     * <p>This check is non-atomic with respect to {@link #markAsProcessed};
     * callers should use {@link #checkAndMark} for atomic check-and-set.
     *
     * @param correlationId the business correlation identifier
     * @return a {@link Mono} emitting {@code true} if already processed
     */
    Mono<Boolean> isAlreadyProcessed(String correlationId);

    /**
     * Records the given correlation ID as processed with the specified TTL.
     *
     * @param correlationId the business correlation identifier to mark
     * @param ttl           how long to retain the record before auto-expiry
     * @return a {@link Mono} completing empty on success
     */
    Mono<Void> markAsProcessed(String correlationId, Duration ttl);

    /**
     * Atomically checks whether the correlation ID is already processed and,
     * if not, marks it as processed. This is the preferred method for consumers
     * as it avoids TOCTOU race conditions.
     *
     * @param correlationId the business correlation identifier
     * @param ttl           how long to retain the idempotency record
     * @return a {@link Mono} emitting {@code true} if this is a <em>new</em>
     *         (not yet processed) event, {@code false} if it is a duplicate
     */
    Mono<Boolean> checkAndMark(String correlationId, Duration ttl);

    /**
     * Removes the idempotency record for the given correlation ID.
     *
     * <p>Should be called when reprocessing a dead-letter entry to allow the
     * consumer to accept the replayed event even if it was previously processed.
     *
     * @param correlationId the correlation identifier whose record should be cleared
     * @return a {@link Mono} completing empty on success
     */
    Mono<Void> clear(String correlationId);
}
