package com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@code tnt_role_sync_outbox} table. Mapped to/from
 * {@link com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry} by
 * {@code RoleSyncOutboxPersistenceMapper}.
 *
 * <p>Unlike {@code RoleEntity}/{@code UserRoleAssignmentEntity}, {@code save()} for this
 * table always goes through a raw SQL {@code INSERT ... ON CONFLICT (id) DO UPDATE}
 * (see {@code RoleSyncOutboxRepositoryAdapter}) rather than
 * {@code ReactiveCrudRepository}/{@code Persistable} — the same entry is re-saved after
 * every state-machine transition, so an explicit upsert is simpler and more direct than
 * simulating one through Spring Data's new-vs-existing detection. This entity type is
 * still {@code @Table}-annotated so {@code R2dbcEntityTemplate} can be used for the plain
 * {@code findByAggregateId} lookup.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_role_sync_outbox")
public class RoleSyncOutboxEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("operation")
    private String operation;

    @Column("aggregate_type")
    private String aggregateType;

    @Column("aggregate_id")
    private UUID aggregateId;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("payload")
    private String payload;

    @Column("status")
    private String status;

    @Column("attempt_count")
    private int attemptCount;

    @Column("last_error")
    private String lastError;

    @Column("kernel_ref_id")
    private UUID kernelRefId;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column("processed_at")
    private LocalDateTime processedAt;

    public RoleSyncOutboxEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public UUID getKernelRefId() {
        return kernelRefId;
    }

    public void setKernelRefId(UUID kernelRefId) {
        this.kernelRefId = kernelRefId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
