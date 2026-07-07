package com.yowyob.tiibntick.core.sync.domain.model;

import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;

import java.time.LocalDateTime;
import java.util.Objects;

public record EntityVersionRecord(
        String tenantId,
        String aggregateType,
        String aggregateId,
        long version,
        DeltaOperation operation,
        String payloadJson,
        LocalDateTime updatedAt,
        String updatedByUserId
) {
    public EntityVersionRecord {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(updatedAt);
    }

    public DeltaRecord toDeltaRecord() {
        return new DeltaRecord(aggregateType, aggregateId, operation, payloadJson, updatedAt, version);
    }

    public String compositeKey() {
        return tenantId + ":" + aggregateType + ":" + aggregateId;
    }
}
