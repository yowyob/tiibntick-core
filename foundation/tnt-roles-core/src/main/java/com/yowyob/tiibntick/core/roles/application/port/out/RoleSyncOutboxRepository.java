package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxSummary;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Secondary (outbound) port for {@link RoleSyncOutboxEntry} persistence.
 *
 * <p>Implementations must support concurrent pollers: {@link #fetchPendingBatch(int)} is
 * expected to use row-level locking (PostgreSQL
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}) so multiple poller instances in a
 * multi-instance deployment never process the same entry twice — see
 * {@code yow-event-kernel}'s {@code R2dbcOutboxEntryRepository} for the closest existing
 * precedent in this repo (a different, Kafka-oriented outbox using the same SQL technique).
 *
 * @author MANFOUO Braun
 */
public interface RoleSyncOutboxRepository {

    /**
     * Persists an entry, upserting on {@code id} — the same entry is saved repeatedly as
     * it moves through its state machine ({@link RoleSyncOutboxEntry#asProcessing()},
     * {@link RoleSyncOutboxEntry#asProvisioned(UUID)}, etc.).
     */
    Mono<RoleSyncOutboxEntry> save(RoleSyncOutboxEntry entry);

    /**
     * Fetches and locks a batch of entries due for processing ({@code PENDING} or
     * {@code RETRYING} with {@code nextAttemptAt <= now}), ordered oldest-first.
     *
     * @param batchSize maximum number of entries to fetch in one pass
     */
    Flux<RoleSyncOutboxEntry> fetchPendingBatch(int batchSize);

    /**
     * Returns every outbox entry recorded for a given local aggregate (role or
     * assignment), across its full history of attempts.
     */
    Flux<RoleSyncOutboxEntry> findByAggregateId(UUID aggregateId);

    /**
     * Returns every outbox entry currently in the given {@code status}, across all
     * tenants/aggregates. Used by {@code KernelRoleReconciliationJob} to enumerate the
     * known-provisioned ({@link RoleSyncStatus#PROVISIONED}) rows worth spot-checking
     * against the Kernel — the outbox is the only place that durably records which local
     * roles/assignments have a Kernel-side counterpart, since the domain {@code Role}/
     * {@code UserRoleAssignment} records themselves carry no {@code kernelRoleId}/
     * {@code kernelAssignmentId} field.
     *
     * <p><strong>Unbounded.</strong> There is no limit, no ordering and no projection: every
     * matching row is transferred and mapped. Never call this to count rows or to build a
     * metric — use {@link #summarise()}. Callers that genuinely need the rows must expect the
     * full set (39 733 {@code PROVISIONED} rows on 27 November 2026).
     */
    Flux<RoleSyncOutboxEntry> findByStatus(RoleSyncStatus status);

    /**
     * Counts and summarises the outbox <strong>in the database</strong>, without transferring
     * or materialising a single row.
     *
     * <p>Use this — never {@link #findByStatus(RoleSyncStatus)} — whenever the question is
     * "how many" rather than "which". The table is unbounded: it held 1 364 000 rows on
     * 27 November 2026, and counting them by loading them saturated three R2DBC event loops
     * and the heap (see {@link RoleSyncOutboxSummary} for the full account).
     *
     * @return the aggregated summary; {@link RoleSyncOutboxSummary#empty()} when the table is empty
     */
    Mono<RoleSyncOutboxSummary> summarise();

    /**
     * One bounded slice of the {@link RoleSyncStatus#PROVISIONED PROVISIONED} rows worth
     * spot-checking against the Kernel, filtered and limited <strong>by the database</strong>.
     *
     * <p>Replaces {@code findByStatus(PROVISIONED)} for {@code KernelRoleReconciliationJob},
     * which checked the whole set on every cycle: 39 733 rows and 39 733 Kernel calls an hour
     * on 27 November 2026. The job spreads the set over {@code bucketCount} slices, one per
     * cycle, so every row is still checked over a full rotation without any single cycle
     * hammering the Kernel.
     *
     * <p>Implementations must keep the slicing <em>stable</em> — the same row must always fall
     * in the same bucket — otherwise a row can be skipped forever or checked repeatedly while
     * others never are.
     *
     * @param bucketCount how many slices the PROVISIONED set is divided into, at least 1
     * @param bucketIndex which slice to return, {@code 0 <= bucketIndex < bucketCount}
     * @param maxRows     hard ceiling on rows returned, whatever the slice holds
     */
    Flux<RoleSyncOutboxEntry> fetchReconciliationBatch(int bucketCount, int bucketIndex, int maxRows);
}
