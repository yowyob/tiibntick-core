package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity.RoleSyncOutboxEntity;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.mapper.RoleSyncOutboxPersistenceMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Implements {@link RoleSyncOutboxRepository}.
 *
 * <p>{@link #fetchPendingBatch(int)} uses a raw SQL query with
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} via {@link DatabaseClient} — Spring Data
 * R2DBC's derived/{@code @Query} methods and {@link R2dbcEntityTemplate} cannot express
 * locking hints. Same technique as {@code yow-event-kernel}'s
 * {@code R2dbcOutboxEntryRepository#fetchPendingBatch}, adapted for this table's two
 * pollable statuses ({@code PENDING}/{@code RETRYING}) and its
 * {@code next_attempt_at}-based scheduling.
 *
 * <p>{@link #save(RoleSyncOutboxEntry)} is an explicit {@code INSERT ... ON CONFLICT (id)
 * DO UPDATE} — the same entry is re-saved after every state-machine transition (see
 * {@link RoleSyncOutboxEntry}), so upserting directly is simpler than simulating one
 * through {@code ReactiveCrudRepository}/{@code Persistable}.
 *
 * <p>Not registered as a Spring bean here — instantiated explicitly by the wiring phase
 * (see {@code TntRolesAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class RoleSyncOutboxRepositoryAdapter implements RoleSyncOutboxRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO tnt_role_sync_outbox
                (id, operation, aggregate_type, aggregate_id, tenant_id, payload, status,
                 attempt_count, last_error, kernel_ref_id, created_at, next_attempt_at, processed_at)
            VALUES
                (:id, :operation, :aggregateType, :aggregateId, :tenantId, :payload, :status,
                 :attemptCount, :lastError, :kernelRefId, :createdAt, :nextAttemptAt, :processedAt)
            ON CONFLICT (id) DO UPDATE SET
                status          = EXCLUDED.status,
                attempt_count   = EXCLUDED.attempt_count,
                last_error      = EXCLUDED.last_error,
                kernel_ref_id   = EXCLUDED.kernel_ref_id,
                next_attempt_at = EXCLUDED.next_attempt_at,
                processed_at    = EXCLUDED.processed_at
            """;

    private static final String FETCH_PENDING_SQL = """
            SELECT *
            FROM   tnt_role_sync_outbox
            WHERE  status IN ('PENDING', 'RETRYING')
              AND  next_attempt_at <= :now
            ORDER  BY created_at ASC
            LIMIT  :batchSize
            FOR UPDATE SKIP LOCKED
            """;

    private final DatabaseClient databaseClient;
    private final R2dbcEntityTemplate template;

    public RoleSyncOutboxRepositoryAdapter(DatabaseClient databaseClient, R2dbcEntityTemplate template) {
        this.databaseClient = Objects.requireNonNull(databaseClient);
        this.template = Objects.requireNonNull(template);
    }

    @Override
    public Mono<RoleSyncOutboxEntry> save(RoleSyncOutboxEntry entry) {
        GenericExecuteSpec spec = databaseClient.sql(UPSERT_SQL)
                .bind("id", entry.id())
                .bind("operation", entry.operation().name())
                .bind("aggregateType", entry.aggregateType().name())
                .bind("aggregateId", entry.aggregateId())
                .bind("tenantId", entry.tenantId())
                .bind("payload", entry.payload())
                .bind("status", entry.status().name())
                .bind("attemptCount", entry.attemptCount())
                .bind("createdAt", entry.createdAt())
                .bind("nextAttemptAt", entry.nextAttemptAt());
        spec = entry.lastError() != null
                ? spec.bind("lastError", entry.lastError())
                : spec.bindNull("lastError", String.class);
        spec = entry.kernelRefId() != null
                ? spec.bind("kernelRefId", entry.kernelRefId())
                : spec.bindNull("kernelRefId", UUID.class);
        spec = entry.processedAt() != null
                ? spec.bind("processedAt", entry.processedAt())
                : spec.bindNull("processedAt", LocalDateTime.class);
        return spec.fetch()
                .rowsUpdated()
                .thenReturn(entry);
    }

    @Override
    public Flux<RoleSyncOutboxEntry> fetchPendingBatch(int batchSize) {
        return databaseClient.sql(FETCH_PENDING_SQL)
                .bind("now", LocalDateTime.now())
                .bind("batchSize", batchSize)
                .map((row, metadata) -> RoleSyncOutboxPersistenceMapper.fromRow(row))
                .all();
    }

    @Override
    public Flux<RoleSyncOutboxEntry> findByAggregateId(UUID aggregateId) {
        return template.select(
                        Query.query(Criteria.where("aggregate_id").is(aggregateId)),
                        RoleSyncOutboxEntity.class)
                .map(RoleSyncOutboxPersistenceMapper::toDomain);
    }

    @Override
    public Flux<RoleSyncOutboxEntry> findByStatus(RoleSyncStatus status) {
        return template.select(
                        Query.query(Criteria.where("status").is(status.name())),
                        RoleSyncOutboxEntity.class)
                .map(RoleSyncOutboxPersistenceMapper::toDomain);
    }
}
