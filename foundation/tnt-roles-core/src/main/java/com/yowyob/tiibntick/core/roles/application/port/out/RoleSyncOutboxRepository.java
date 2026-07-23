package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
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
     */
    Flux<RoleSyncOutboxEntry> findByStatus(RoleSyncStatus status);
}
