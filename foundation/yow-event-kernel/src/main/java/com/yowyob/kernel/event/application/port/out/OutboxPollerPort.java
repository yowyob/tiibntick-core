package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.OutboxEntry;

/**
 * <b>Outbound port</b> — Drives the transactional outbox polling loop.
 *
 * <p>The poller is the heart of the at-least-once delivery guarantee. On each
 * polling cycle it:
 * <ol>
 *   <li>Acquires a batch of PENDING entries with a DB-level row lock.</li>
 *   <li>Loads the corresponding {@link yowyob.kernel.event.domain.model.DomainEventEnvelope}s.</li>
 *   <li>Publishes each envelope to Kafka via {@link KafkaPublisherPort}.</li>
 *   <li>Updates the entry status to PROCESSED or FAILED.</li>
 * </ol>
 *
 * <p>Implementations must be scheduled via a {@code @Scheduled} fixed-delay
 * annotation (configurable via {@code yow.event.outbox.poll-interval-ms}).
 * Concurrent execution is prevented by the {@code SKIP LOCKED} locking strategy
 * in {@link OutboxEntryRepository#fetchPendingBatch}.
 */
public interface OutboxPollerPort {

    /**
     * Executes one polling cycle: fetches a batch of pending outbox entries
     * and publishes them to Kafka.
     *
     * @return a {@link Mono} emitting the number of entries processed in this cycle
     */
    Mono<Integer> poll();

    /**
     * Processes a single outbox entry (used for targeted reprocessing or testing).
     *
     * @param entry the outbox entry to process
     * @return a {@link Mono} completing empty on success, or propagating an error
     */
    Mono<Void> processEntry(OutboxEntry entry);

    /**
     * Returns {@code true} if a polling cycle is currently in progress.
     * Used to prevent overlapping poll executions.
     *
     * @return {@code true} if polling is active
     */
    boolean isPolling();
}
