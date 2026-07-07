package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.enums.OutboxStatus;
import com.yowyob.kernel.event.domain.model.OutboxEntry;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.OutboxEntryId;

import java.time.LocalDateTime;

/**
 * <b>Outbound port</b> — Persistent storage for the transactional outbox.
 *
 * <p>The outbox table acts as a staging area between the business database
 * transaction and the Kafka publish operation, guaranteeing at-least-once
 * delivery even in the presence of application or network failures.
 *
 * <p>Implementations must use pessimistic row-level locking (PostgreSQL
 * {@code SELECT … FOR UPDATE SKIP LOCKED}) when fetching PENDING entries
 * to support concurrent pollers without duplicate delivery.
 */
public interface OutboxEntryRepository {

    /**
     * Persists a new outbox entry within the current transaction.
     *
     * @param entry the entry to save
     * @return a {@link Mono} emitting the persisted entry
     */
    Mono<OutboxEntry> save(OutboxEntry entry);

    /**
     * Fetches a batch of PENDING entries for processing.
     *
     * <p>The implementation <strong>must</strong> acquire a row-level lock
     * ({@code SKIP LOCKED}) so that concurrent poller instances do not process
     * the same entry twice.
     *
     * @param batchSize maximum number of entries to fetch in one pass
     * @return a {@link Flux} of at most {@code batchSize} locked PENDING entries
     */
    Flux<OutboxEntry> fetchPendingBatch(int batchSize);

    /**
     * Fetches entries that failed and are due for retry, based on the scheduled
     * retry timestamp stored in the entry.
     *
     * @param now       the current timestamp to compare against retry schedule
     * @param batchSize maximum number of entries to retrieve
     * @return a {@link Flux} of retryable entries
     */
    Flux<OutboxEntry> fetchRetryableBatch(LocalDateTime now, int batchSize);

    /**
     * Retrieves an outbox entry by its unique ID.
     *
     * @param id the entry identifier
     * @return a {@link Mono} emitting the entry, or empty if not found
     */
    Mono<OutboxEntry> findById(OutboxEntryId id);

    /**
     * Retrieves the outbox entry associated with a specific envelope.
     *
     * @param envelopeId the envelope identifier
     * @return a {@link Mono} emitting the entry, or empty if not found
     */
    Mono<OutboxEntry> findByEnvelopeId(EnvelopeId envelopeId);

    /**
     * Updates the processing status of an existing entry.
     *
     * @param id          the entry to update
     * @param status      the new processing status
     * @param processedAt the timestamp of successful processing (may be {@code null})
     * @return a {@link Mono} emitting the number of updated rows
     */
    Mono<Long> updateStatus(OutboxEntryId id, OutboxStatus status, LocalDateTime processedAt);

    /**
     * Deletes all entries that have been successfully processed and are older
     * than the retention threshold.
     *
     * <p>Called by the scheduled cleanup job to prevent unbounded table growth.
     *
     * @param olderThan all PROCESSED entries with {@code processed_at} before this
     *                  timestamp will be deleted
     * @return a {@link Mono} emitting the number of deleted rows
     */
    Mono<Long> deleteProcessedOlderThan(LocalDateTime olderThan);

    /**
     * Returns the count of PENDING entries for observability and alerting.
     *
     * @return a {@link Mono} emitting the backlog size
     */
    Mono<Long> countPending();
}
