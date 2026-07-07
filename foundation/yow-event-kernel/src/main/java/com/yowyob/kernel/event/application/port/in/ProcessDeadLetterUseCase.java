package com.yowyob.kernel.event.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.vo.DeadLetterEntryId;

/**
 * <b>Inbound port</b> — Manage entries in the Dead-Letter Queue (DLQ).
 *
 * <p>Entries arrive in the DLQ after a {@link yowyob.kernel.event.domain.model.DomainEventEnvelope}
 * has exhausted all configured retry attempts. Operators use this port to:
 * <ol>
 *   <li>Inspect the failed entries and understand the root cause.</li>
 *   <li>Fix the underlying infrastructure or data issue.</li>
 *   <li>Trigger reprocessing to redeliver the event.</li>
 *   <li>Explicitly discard entries that are no longer relevant.</li>
 * </ol>
 *
 * <p>All operations are tenant-scoped. Administrators with a cross-tenant role
 * may pass {@code null} as {@code tenantId} to operate across all tenants.
 */
public interface ProcessDeadLetterUseCase {

    /**
     * Lists all DLQ entries with {@link yowyob.kernel.event.domain.enums.DLQStatus#WAITING}
     * status for the given tenant.
     *
     * @param tenantId the owning tenant ({@code null} for all tenants — admin only)
     * @return a {@link Flux} of waiting DLQ entries, ordered by {@code failedAt} ascending
     */
    Flux<DeadLetterEntry> listWaiting(String tenantId);

    /**
     * Retrieves a single DLQ entry by its identifier.
     *
     * @param id       the DLQ entry identifier
     * @param tenantId the owning tenant
     * @return a {@link Mono} emitting the entry, or empty if not found
     */
    Mono<DeadLetterEntry> findById(DeadLetterEntryId id, String tenantId);

    /**
     * Triggers reprocessing of the specified DLQ entry.
     *
     * <p>The entry's payload is re-submitted to the Kafka publishing pipeline.
     * If publishing succeeds, the entry transitions to
     * {@link yowyob.kernel.event.domain.enums.DLQStatus#REPROCESSED}.
     *
     * @param id       the DLQ entry to reprocess
     * @param tenantId the owning tenant
     * @return a {@link Mono} completing empty on success, or propagating an
     *         error if reprocessing fails
     */
    Mono<Void> reprocess(DeadLetterEntryId id, String tenantId);

    /**
     * Reprocesses all WAITING entries for the given Kafka topic within a
     * tenant, useful for bulk recovery after a topic-level outage.
     *
     * @param kafkaTopic the topic whose dead-letter entries should be reprocessed
     * @param tenantId   the owning tenant
     * @return a {@link Mono} emitting the count of entries submitted for reprocessing
     */
    Mono<Long> reprocessByTopic(String kafkaTopic, String tenantId);

    /**
     * Explicitly discards a DLQ entry, preventing any future reprocessing.
     *
     * @param id       the DLQ entry to discard
     * @param tenantId the owning tenant
     * @param reason   the administrative justification for discarding
     * @return a {@link Mono} completing empty on success
     */
    Mono<Void> discard(DeadLetterEntryId id, String tenantId, String reason);

    /**
     * Returns the total count of WAITING DLQ entries for the given tenant.
     *
     * @param tenantId the owning tenant
     * @return a {@link Mono} emitting the count
     */
    Mono<Long> countWaiting(String tenantId);
}
