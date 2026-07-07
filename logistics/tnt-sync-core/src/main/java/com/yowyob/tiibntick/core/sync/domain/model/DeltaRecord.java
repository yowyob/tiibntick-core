package com.yowyob.tiibntick.core.sync.domain.model;

import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;

import java.time.LocalDateTime;
import java.util.Objects;

public record DeltaRecord(
        String aggregateType,
        String aggregateId,
        DeltaOperation operation,
        String payload,
        LocalDateTime serverTimestamp,
        long serverVersion
) {
    public DeltaRecord {
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(serverTimestamp);
    }

    public boolean isDeleted() {
        return operation == DeltaOperation.DELETED;
    }
}
