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
     * Puts back into the queue every entry claimed more than {@code staleAfter} ago and still
     * in {@link RoleSyncStatus#PROCESSING} — a claim whose process never finished.
     *
     * <p>{@link #fetchPendingBatch(int)} only ever returns {@code PENDING} and
     * {@code RETRYING} rows, so a row left in {@code PROCESSING} by a restart, an OOM kill or
     * a lost instance has <em>no recovery path whatsoever</em>: it is neither retried nor
     * declared dead, and no count reports it as late. There were 274 such rows on
     * 27 November 2026 — work accepted and silently dropped.
     *
     * <p>The claim instant is {@code nextAttemptAt}, stamped by
     * {@link RoleSyncOutboxEntry#asProcessing()}. Implementations must requeue
     * <em>in the database</em>, in one statement, without transferring rows.
     *
     * <p>{@code staleAfter} must stay well above the longest legitimate attempt (the Kernel
     * call timeout, and the poller's {@code lockAtMostFor}), otherwise a row still being
     * processed elsewhere is handed to a second instance. The attempt counter is not reset:
     * a row that keeps being claimed and lost still reaches {@code DEAD} and stops.
     *
     * @param staleAfter how long a claim may stay unfinished before it is considered lost
     * @return the number of entries put back into the queue
     */
    Mono<Long> requeueStaleClaims(java.time.Duration staleAfter);

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
     * One bounded slice of the aggregates that have a
     * {@link RoleSyncStatus#PROVISIONED PROVISIONED} row worth spot-checking against the
     * Kernel — <strong>at most one row per {@code aggregateId}, the most recent</strong> —
     * filtered and limited by the database.
     *
     * <p>Replaces {@code findByStatus(PROVISIONED)} for {@code KernelRoleReconciliationJob},
     * which checked every row on every cycle: 39 733 rows and 39 733 Kernel calls an hour on
     * 27 November 2026, for exactly 29 distinct aggregates. Because this table is append-only
     * per attempt, checking rows made the job amplify itself — one absent assignment produced
     * one re-enqueue per historical row, and each re-enqueue that succeeded added another row
     * for the next cycle to find.
     *
     * <p>Two obligations on implementations. The result must hold <em>one row per aggregate</em>
     * (the newest), or the amplification returns. And the slicing must be <em>stable per
     * aggregate</em> — the same aggregate always in the same bucket, independently of which of
     * its rows is currently newest — otherwise a rotation can keep missing it.
     *
     * @param bucketCount how many slices the aggregate set is divided into, at least 1
     * @param bucketIndex which slice to return, {@code 0 <= bucketIndex < bucketCount}
     * @param maxRows     hard ceiling on rows returned, whatever the slice holds
     */
    Flux<RoleSyncOutboxEntry> fetchReconciliationBatch(int bucketCount, int bucketIndex, int maxRows);
}
