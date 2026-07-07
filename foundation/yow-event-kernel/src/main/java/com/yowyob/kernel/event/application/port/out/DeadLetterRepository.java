package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.enums.DLQStatus;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.vo.DeadLetterEntryId;

/**
 * <b>Outbound port</b> — Persistent storage for Dead-Letter Queue entries.
 *
 * <p>DLQ entries represent envelopes that have permanently failed delivery.
 * They are stored in a dedicated table so that operators can inspect, reprocess
 * or discard them without interfering with the main outbox flow.
 */
public interface DeadLetterRepository {

    /**
     * Persists a new dead-letter entry.
     *
     * @param entry the entry to save
     * @return a {@link Mono} emitting the persisted entry
     */
    Mono<DeadLetterEntry> save(DeadLetterEntry entry);

    /**
     * Retrieves a dead-letter entry by its identifier.
     *
     * @param id       the entry identifier
     * @param tenantId the owning tenant
     * @return a {@link Mono} emitting the entry, or empty if not found
     */
    Mono<DeadLetterEntry> findById(DeadLetterEntryId id, String tenantId);

    /**
     * Retrieves all WAITING entries for a given tenant, ordered oldest-first.
     *
     * @param tenantId the owning tenant ({@code null} for all tenants — admin only)
     * @return a {@link Flux} of waiting entries
     */
    Flux<DeadLetterEntry> findWaiting(String tenantId);

    /**
     * Retrieves WAITING entries for a specific Kafka topic (used for bulk recovery).
     *
     * @param kafkaTopic the target topic
     * @param tenantId   the owning tenant
     * @return a {@link Flux} of matching entries
     */
    Flux<DeadLetterEntry> findWaitingByTopic(String kafkaTopic, String tenantId);

    /**
     * Updates the status of a DLQ entry after reprocessing or discarding.
     *
     * @param id             the entry identifier
     * @param status         the new status
     * @param reprocessedAt  the reprocessing timestamp (may be {@code null})
     * @param discardReason  the discard reason (may be {@code null})
     * @return a {@link Mono} emitting the updated row count
     */
    Mono<Long> updateStatus(
            DeadLetterEntryId id,
            DLQStatus status,
            java.time.LocalDateTime reprocessedAt,
            String discardReason);

    /**
     * Counts the total number of WAITING DLQ entries for a tenant.
     *
     * @param tenantId the owning tenant
     * @return a {@link Mono} emitting the count
     */
    Mono<Long> countWaiting(String tenantId);
}
