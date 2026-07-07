package com.yowyob.tiibntick.core.sync.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("tnt_offline_operation")
public class OfflineOperationEntity {

    @Id
    @Column("id")
    public String id;

    @Column("user_id")
    public String userId;

    @Column("tenant_id")
    public String tenantId;

    @Column("device_id")
    public String deviceId;

    @Column("type")
    public String type;

    @Column("aggregate_type")
    public String aggregateType;

    @Column("aggregate_id")
    public String aggregateId;

    @Column("payload")
    public String payload;

    @Column("local_timestamp")
    public LocalDateTime localTimestamp;

    @Column("sequence_number")
    public long sequenceNumber;

    @Column("status")
    public String status;

    @Column("retry_count")
    public int retryCount;

    @Column("last_attempt_at")
    public LocalDateTime lastAttemptAt;

    @Column("error")
    public String error;

    @Column("created_at")
    public LocalDateTime createdAt;

    public com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation toDomain() {
        var op = new com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation(
                com.yowyob.tiibntick.core.sync.domain.model.OfflineOpId.of(id),
                userId, tenantId, deviceId,
                OfflineOpType.valueOf(type),
                aggregateType, aggregateId, payload,
                localTimestamp, sequenceNumber
        );
        return op;
    }

    public static OfflineOperationEntity fromDomain(com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation op) {
        var e = new OfflineOperationEntity();
        e.id = op.getId().value();
        e.userId = op.getUserId();
        e.tenantId = op.getTenantId();
        e.deviceId = op.getDeviceId();
        e.type = op.getType().name();
        e.aggregateType = op.getAggregateType();
        e.aggregateId = op.getAggregateId();
        e.payload = op.getPayload();
        e.localTimestamp = op.getLocalTimestamp();
        e.sequenceNumber = op.getSequenceNumber();
        e.status = op.getStatus().name();
        e.retryCount = op.getRetryCount();
        e.lastAttemptAt = op.getLastAttemptAt();
        e.error = op.getError();
        e.createdAt = LocalDateTime.now();
        return e;
    }
}
