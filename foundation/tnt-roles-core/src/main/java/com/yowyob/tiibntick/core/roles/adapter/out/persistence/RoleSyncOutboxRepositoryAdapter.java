package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity.RoleSyncOutboxEntity;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.mapper.RoleSyncOutboxPersistenceMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxSummary;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    /**
     * Counts per status in one grouped aggregate — no row leaves the database. Served by the
     * {@code (status, created_at)} index added in changeset 004 as an index-only scan.
     */
    private static final String COUNT_BY_STATUS_SQL = """
            SELECT status, count(*) AS total
            FROM   tnt_role_sync_outbox
            GROUP  BY status
            """;

    /** Counts of DEAD rows per operation — the {@code dead_by_operation} health detail. */
    private static final String DEAD_BY_OPERATION_SQL = """
            SELECT operation, count(*) AS total
            FROM   tnt_role_sync_outbox
            WHERE  status = 'DEAD'
            GROUP  BY operation
            """;

    /**
     * The two DEAD extremes and the oldest one's error, in a single pass — never the 401 733
     * rows themselves.
     */
    private static final String DEAD_EXTREMES_SQL = """
            SELECT min(created_at) AS oldest,
                   max(created_at) AS newest,
                   (SELECT last_error
                      FROM tnt_role_sync_outbox
                     WHERE status = 'DEAD'
                     ORDER BY created_at ASC
                     LIMIT 1) AS oldest_error
            FROM   tnt_role_sync_outbox
            WHERE  status = 'DEAD'
            """;

    /**
     * One stable slice of the PROVISIONED rows the reconciliation job may check.
     *
     * <p><b>One row per aggregate, the most recent.</b> This table is append-only per attempt:
     * every re-enqueue of the same role or assignment leaves another row behind. Checking rows
     * instead of aggregates therefore made the job amplify itself — a single assignment absent
     * from the Kernel produced one re-enqueue per historical row, and every re-enqueue that
     * then succeeded added another PROVISIONED row for the next cycle to find. Measured on
     * 27 November 2026: 39 733 PROVISIONED rows for exactly 29 distinct aggregates, 39 672 of
     * them the same assignment, grown from nothing between 9 and 25 November; 940 402 PENDING
     * and 401 838 DEAD rows were the output of that loop. {@code DISTINCT ON} collapses the
     * set back to what it is actually about: 29 things to verify, not 39 733.
     *
     * <p>The slice key is the first byte of {@code aggregate_id} (via {@code uuid_send}),
     * uniform for the random v4 ids used here and always non-negative — unlike {@code hashtext},
     * whose result can be negative and whose {@code abs()} overflows on
     * {@code Integer.MIN_VALUE}. Keyed on the aggregate rather than the row so that an
     * aggregate stays in the same bucket even as its newest row changes; otherwise a rotation
     * could keep missing it.
     *
     * <p>The operation filter is applied here as well as in the job: the job needs it as a
     * domain invariant, the query needs it so the LIMIT is not spent on rows that would be
     * discarded afterwards.
     */
    private static final String RECONCILIATION_BATCH_SQL = """
            SELECT *
            FROM (
                SELECT DISTINCT ON (aggregate_id) *
                FROM   tnt_role_sync_outbox
                WHERE  status = 'PROVISIONED'
                  AND  operation IN ('PROVISION_ROLE', 'ASSIGN_ROLE')
                ORDER  BY aggregate_id, created_at DESC
            ) latest_per_aggregate
            WHERE (get_byte(uuid_send(aggregate_id), 0) % :bucketCount) = :bucketIndex
            ORDER BY created_at ASC
            LIMIT :maxRows
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

    /**
     * Three grouped/aggregate queries instead of three full scans materialised into Java
     * objects. An unknown {@code status}/{@code operation} value found in the table is skipped
     * rather than failing the whole summary: a monitoring read must never be the thing that
     * breaks on an unexpected enum value.
     */
    @Override
    public Mono<RoleSyncOutboxSummary> summarise() {
        Mono<Map<RoleSyncStatus, Long>> byStatus = databaseClient.sql(COUNT_BY_STATUS_SQL)
                .map((row, metadata) -> Map.entry(
                        String.valueOf(row.get("status", String.class)),
                        total(row.get("total", Long.class))))
                .all()
                .reduce(new EnumMap<RoleSyncStatus, Long>(RoleSyncStatus.class), (acc, e) -> {
                    parse(RoleSyncStatus.class, e.getKey())
                            .ifPresent(status -> acc.merge(status, e.getValue(), Long::sum));
                    return acc;
                })
                .map(Map::copyOf);

        Mono<Map<RoleSyncOperation, Long>> deadByOperation = databaseClient.sql(DEAD_BY_OPERATION_SQL)
                .map((row, metadata) -> Map.entry(
                        String.valueOf(row.get("operation", String.class)),
                        total(row.get("total", Long.class))))
                .all()
                .reduce(new EnumMap<RoleSyncOperation, Long>(RoleSyncOperation.class), (acc, e) -> {
                    parse(RoleSyncOperation.class, e.getKey())
                            .ifPresent(operation -> acc.merge(operation, e.getValue(), Long::sum));
                    return acc;
                })
                .map(Map::copyOf);

        Mono<DeadExtremes> extremes = databaseClient.sql(DEAD_EXTREMES_SQL)
                .map((row, metadata) -> new DeadExtremes(
                        row.get("oldest", LocalDateTime.class),
                        row.get("newest", LocalDateTime.class),
                        row.get("oldest_error", String.class)))
                .one()
                .defaultIfEmpty(new DeadExtremes(null, null, null));

        return Mono.zip(byStatus, deadByOperation, extremes)
                .map(t -> new RoleSyncOutboxSummary(
                        t.getT1(), t.getT2(),
                        t.getT3().oldest(), t.getT3().oldestError(), t.getT3().newest()));
    }

    private static long total(Long value) {
        return value == null ? 0L : value;
    }

    private static <E extends Enum<E>> Optional<E> parse(Class<E> type, String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Flux<RoleSyncOutboxEntry> fetchReconciliationBatch(int bucketCount, int bucketIndex, int maxRows) {
        if (bucketCount < 1) {
            return Flux.error(new IllegalArgumentException("bucketCount must be at least 1, got " + bucketCount));
        }
        if (bucketIndex < 0 || bucketIndex >= bucketCount) {
            return Flux.error(new IllegalArgumentException(
                    "bucketIndex must be in [0, " + bucketCount + "), got " + bucketIndex));
        }
        if (maxRows < 1) {
            return Flux.error(new IllegalArgumentException("maxRows must be at least 1, got " + maxRows));
        }
        return databaseClient.sql(RECONCILIATION_BATCH_SQL)
                .bind("bucketCount", bucketCount)
                .bind("bucketIndex", bucketIndex)
                .bind("maxRows", maxRows)
                .map((row, metadata) -> RoleSyncOutboxPersistenceMapper.fromRow(row))
                .all();
    }

    private record DeadExtremes(LocalDateTime oldest, LocalDateTime newest, String oldestError) {
    }
}
