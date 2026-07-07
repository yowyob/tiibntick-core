package com.yowyob.tiibntick.core.sync.domain.model;

import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpStatus;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class OfflineOperation {

    private static final int MAX_RETRY_COUNT = 3;

    private final OfflineOpId id;
    private final String userId;
    private final String tenantId;
    private final String deviceId;
    private final OfflineOpType type;
    private final String aggregateType;
    private final String aggregateId;
    private final String payload;
    private final LocalDateTime localTimestamp;
    private final long sequenceNumber;

    private OfflineOpStatus status;
    private int retryCount;
    private LocalDateTime lastAttemptAt;
    private String error;

    public OfflineOperation(OfflineOpId id, String userId, String tenantId, String deviceId,
                            OfflineOpType type, String aggregateType, String aggregateId,
                            String payload, LocalDateTime localTimestamp, long sequenceNumber) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = deviceId;
        this.type = Objects.requireNonNull(type);
        this.aggregateType = Objects.requireNonNull(aggregateType);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.payload = Objects.requireNonNull(payload);
        this.localTimestamp = Objects.requireNonNull(localTimestamp);
        this.sequenceNumber = sequenceNumber;
        this.status = OfflineOpStatus.QUEUED;
        this.retryCount = 0;
    }

    public void markApplying() {
        this.status = OfflineOpStatus.APPLYING;
        this.lastAttemptAt = LocalDateTime.now();
        this.retryCount++;
    }

    public void markApplied() {
        this.status = OfflineOpStatus.APPLIED;
        this.error = null;
    }

    public void markFailed(String error) {
        this.status = OfflineOpStatus.FAILED;
        this.error = error;
    }

    public void markConflict() {
        this.status = OfflineOpStatus.CONFLICT;
    }

    public void markDiscarded(String reason) {
        this.status = OfflineOpStatus.DISCARDED;
        this.error = reason;
    }

    public void resetToQueued() {
        this.status = OfflineOpStatus.QUEUED;
    }

    public boolean canRetry() {
        return retryCount < MAX_RETRY_COUNT
                && (status == OfflineOpStatus.FAILED || status == OfflineOpStatus.QUEUED);
    }

    public boolean isPending() {
        return status == OfflineOpStatus.QUEUED || status == OfflineOpStatus.APPLYING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OfflineOperation op)) return false;
        return Objects.equals(id, op.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "OfflineOperation{id=" + id + ", type=" + type + ", aggregate=" + aggregateType + "/" + aggregateId + ", status=" + status + "}";
    }
}
