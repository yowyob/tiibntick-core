package com.yowyob.tiibntick.core.roles.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity.RoleSyncOutboxEntity;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import io.r2dbc.spi.Row;

/**
 * Hand-written entity/domain mapper for {@link RoleSyncOutboxEntry}, same style as
 * {@code RolePersistenceMapper}. Also provides {@link #fromRow} for the raw-SQL
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} path in {@code RoleSyncOutboxRepositoryAdapter},
 * matching {@code yow-event-kernel}'s {@code OutboxEntryMapper#fromRow} precedent.
 *
 * @author MANFOUO Braun
 */
public final class RoleSyncOutboxPersistenceMapper {

    private RoleSyncOutboxPersistenceMapper() {
    }

    public static RoleSyncOutboxEntry toDomain(RoleSyncOutboxEntity e) {
        if (e == null) return null;
        return new RoleSyncOutboxEntry(
                e.getId(),
                RoleSyncOperation.valueOf(e.getOperation()),
                RoleSyncAggregateType.valueOf(e.getAggregateType()),
                e.getAggregateId(),
                e.getTenantId(),
                e.getPayload(),
                RoleSyncStatus.valueOf(e.getStatus()),
                e.getAttemptCount(),
                e.getLastError(),
                e.getKernelRefId(),
                e.getCreatedAt(),
                e.getNextAttemptAt(),
                e.getProcessedAt());
    }

    public static RoleSyncOutboxEntity toEntity(RoleSyncOutboxEntry d) {
        if (d == null) return null;
        RoleSyncOutboxEntity e = new RoleSyncOutboxEntity();
        e.setId(d.id());
        e.setOperation(d.operation().name());
        e.setAggregateType(d.aggregateType().name());
        e.setAggregateId(d.aggregateId());
        e.setTenantId(d.tenantId());
        e.setPayload(d.payload());
        e.setStatus(d.status().name());
        e.setAttemptCount(d.attemptCount());
        e.setLastError(d.lastError());
        e.setKernelRefId(d.kernelRefId());
        e.setCreatedAt(d.createdAt());
        e.setNextAttemptAt(d.nextAttemptAt());
        e.setProcessedAt(d.processedAt());
        return e;
    }

    /**
     * Builds a {@link RoleSyncOutboxEntry} directly from a raw {@code r2dbc-postgresql}
     * {@link Row} — used by the {@code SELECT ... FOR UPDATE SKIP LOCKED} query, which
     * cannot go through {@code R2dbcEntityTemplate}.
     */
    public static RoleSyncOutboxEntry fromRow(Row row) {
        return new RoleSyncOutboxEntry(
                row.get("id", java.util.UUID.class),
                RoleSyncOperation.valueOf(row.get("operation", String.class)),
                RoleSyncAggregateType.valueOf(row.get("aggregate_type", String.class)),
                row.get("aggregate_id", java.util.UUID.class),
                row.get("tenant_id", java.util.UUID.class),
                row.get("payload", String.class),
                RoleSyncStatus.valueOf(row.get("status", String.class)),
                row.get("attempt_count", Integer.class) == null ? 0 : row.get("attempt_count", Integer.class),
                row.get("last_error", String.class),
                row.get("kernel_ref_id", java.util.UUID.class),
                row.get("created_at", java.time.LocalDateTime.class),
                row.get("next_attempt_at", java.time.LocalDateTime.class),
                row.get("processed_at", java.time.LocalDateTime.class));
    }
}
