package com.yowyob.tiibntick.core.roles.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A transactional-outbox entry queuing a local RBAC write ({@link Role} or
 * {@link UserRoleAssignment}) for eventual sync to the Kernel over HTTP.
 *
 * <p>Local RBAC persistence (Chantier D · Audit n°6 · S5) lets {@code tnt-roles-core}
 * accept role/assignment writes without a synchronous, in-request round-trip to the
 * Kernel. Each write appends a {@code RoleSyncOutboxEntry} in the same local transaction;
 * a poller (built in a later phase, mirroring {@code yow-event-kernel}'s
 * {@code OutboxPollerService}) drains {@code PENDING}/{@code RETRYING} rows and replays
 * them against the Kernel's HTTP role/assignment endpoints.
 *
 * <p><b>State machine:</b>
 * <pre>
 *                 ┌────────────────────────────────────────────────────────┐
 *                 │                                                        │
 *                 ▼                                                        │
 *   PENDING ──asProcessing()──&gt; PROCESSING ──asProvisioned()──&gt; PROVISIONED (terminal)
 *                                    │
 *                                    ├──asRetrying()──&gt; RETRYING ──asProcessing()──┘
 *                                    │
 *                                    └──asDead()──&gt; DEAD (terminal)
 * </pre>
 * <ul>
 *   <li>{@link #pending} creates a new entry in {@link RoleSyncStatus#PENDING}, ready for
 *       the poller to pick up.</li>
 *   <li>{@link #asProcessing()} — the poller claimed the entry (typically via
 *       {@code SELECT ... FOR UPDATE SKIP LOCKED}); the attempt counter is incremented so
 *       repeated failures can be counted even if the process crashes mid-attempt.</li>
 *   <li>{@link #asProvisioned(UUID)} — the Kernel call succeeded; the returned Kernel-side
 *       identifier is recorded and {@code processedAt} is stamped. Terminal state.</li>
 *   <li>{@link #asRetrying(String, LocalDateTime)} — the Kernel call failed but retries
 *       remain; the error is recorded and the entry is rescheduled for
 *       {@code nextAttemptAt}, to be picked up by the poller again.</li>
 *   <li>{@link #asDead(String)} — the Kernel call failed and retries are exhausted (a
 *       decision made by the poller, not by this type); the entry stops being polled and
 *       is left for manual/ops inspection. Terminal state.</li>
 * </ul>
 *
 * <p>Deliberately not a JPA/R2DBC entity — this is a pure domain value object, mapped
 * to/from {@code RoleSyncOutboxEntity} by the persistence adapter, matching the style of
 * {@link Role} and {@link UserRoleAssignment} in this package.
 *
 * @author MANFOUO Braun
 */
public record RoleSyncOutboxEntry(
        UUID id,
        RoleSyncOperation operation,
        RoleSyncAggregateType aggregateType,
        UUID aggregateId,
        UUID tenantId,
        String payload,
        RoleSyncStatus status,
        int attemptCount,
        String lastError,
        UUID kernelRefId,
        LocalDateTime createdAt,
        LocalDateTime nextAttemptAt,
        LocalDateTime processedAt
) {

    /**
     * Creates a new outbox entry in {@link RoleSyncStatus#PENDING}, ready for the poller
     * to pick up immediately ({@code nextAttemptAt} = now).
     *
     * @param operation     the Kernel-facing operation this entry drives
     * @param aggregateType whether {@code aggregateId} refers to a {@link Role} or a
     *                      {@link UserRoleAssignment}
     * @param aggregateId   the local aggregate's id
     * @param tenantId      tenant the write belongs to
     * @param payload       JSON snapshot of the aggregate, parsed by the poller/worker
     * @return a new PENDING entry with a fresh id, zero attempts, and no error/kernel ref
     */
    public static RoleSyncOutboxEntry pending(RoleSyncOperation operation, RoleSyncAggregateType aggregateType,
            UUID aggregateId, UUID tenantId, String payload) {
        LocalDateTime now = LocalDateTime.now();
        return new RoleSyncOutboxEntry(
                UUID.randomUUID(),
                operation,
                aggregateType,
                aggregateId,
                tenantId,
                payload,
                RoleSyncStatus.PENDING,
                0,
                null,
                null,
                now,
                now,
                null
        );
    }

    /**
     * Transitions to {@link RoleSyncStatus#PROCESSING} and increments the attempt counter.
     * Called by the poller right after claiming this row.
     *
     * @return a copy with {@code status=PROCESSING} and {@code attemptCount+1}
     */
    public RoleSyncOutboxEntry asProcessing() {
        return new RoleSyncOutboxEntry(
                id, operation, aggregateType, aggregateId, tenantId, payload,
                RoleSyncStatus.PROCESSING, attemptCount + 1, lastError, kernelRefId,
                createdAt, nextAttemptAt, processedAt
        );
    }

    /**
     * Transitions to the terminal {@link RoleSyncStatus#PROVISIONED} state after a
     * successful Kernel call.
     *
     * @param kernelRefId the Kernel-side id returned for this role/assignment
     * @return a copy with {@code status=PROVISIONED}, {@code lastError=null},
     *         {@code kernelRefId} set, and {@code processedAt} stamped to now
     */
    public RoleSyncOutboxEntry asProvisioned(UUID kernelRefId) {
        return new RoleSyncOutboxEntry(
                id, operation, aggregateType, aggregateId, tenantId, payload,
                RoleSyncStatus.PROVISIONED, attemptCount, null, kernelRefId,
                createdAt, nextAttemptAt, LocalDateTime.now()
        );
    }

    /**
     * Transitions to {@link RoleSyncStatus#RETRYING} after a failed Kernel call, when
     * retries remain. Records the error and reschedules the next attempt.
     *
     * @param error         the failure detail, retained for observability/debugging
     * @param nextAttemptAt when the poller should retry this entry
     * @return a copy with {@code status=RETRYING}, {@code lastError} set, and
     *         {@code nextAttemptAt} updated
     */
    public RoleSyncOutboxEntry asRetrying(String error, LocalDateTime nextAttemptAt) {
        return new RoleSyncOutboxEntry(
                id, operation, aggregateType, aggregateId, tenantId, payload,
                RoleSyncStatus.RETRYING, attemptCount, error, kernelRefId,
                createdAt, nextAttemptAt, processedAt
        );
    }

    /**
     * Transitions to the terminal {@link RoleSyncStatus#DEAD} state once the poller has
     * exhausted the configured retry budget for this entry.
     *
     * @param error the final failure detail
     * @return a copy with {@code status=DEAD}, {@code lastError} set, and
     *         {@code processedAt} stamped to now
     */
    public RoleSyncOutboxEntry asDead(String error) {
        return new RoleSyncOutboxEntry(
                id, operation, aggregateType, aggregateId, tenantId, payload,
                RoleSyncStatus.DEAD, attemptCount, error, kernelRefId,
                createdAt, nextAttemptAt, LocalDateTime.now()
        );
    }
}
