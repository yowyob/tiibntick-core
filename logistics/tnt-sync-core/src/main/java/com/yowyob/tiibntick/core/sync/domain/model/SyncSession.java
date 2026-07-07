package com.yowyob.tiibntick.core.sync.domain.model;

import com.yowyob.tiibntick.core.sync.domain.model.enums.SyncSessionStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class SyncSession {

    private final SyncSessionId id;
    private final String userId;
    private final String tenantId;
    private final String deviceId;
    private final SyncToken sinceToken;
    private final LocalDateTime startedAt;

    private LocalDateTime completedAt;
    private int operationsSubmitted;
    private int operationsApplied;
    private int conflictsDetected;
    private int conflictsResolved;
    private SyncSessionStatus status;
    private SyncToken resultToken;

    public SyncSession(SyncSessionId id, String userId, String tenantId, String deviceId, SyncToken sinceToken) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = deviceId;
        this.sinceToken = Objects.requireNonNull(sinceToken);
        this.startedAt = LocalDateTime.now();
        this.status = SyncSessionStatus.IN_PROGRESS;
    }

    public void recordPushStats(int submitted, int applied, int conflictsDetected, int conflictsResolved) {
        this.operationsSubmitted = submitted;
        this.operationsApplied = applied;
        this.conflictsDetected = conflictsDetected;
        this.conflictsResolved = conflictsResolved;
    }

    public void complete(SyncToken resultToken) {
        this.resultToken = Objects.requireNonNull(resultToken);
        this.completedAt = LocalDateTime.now();
        boolean partial = operationsSubmitted > 0 && operationsApplied < operationsSubmitted;
        this.status = partial ? SyncSessionStatus.PARTIAL : SyncSessionStatus.COMPLETED;
    }

    public void fail(String reason) {
        this.completedAt = LocalDateTime.now();
        this.status = SyncSessionStatus.FAILED;
    }

    public boolean isCompleted() {
        return status == SyncSessionStatus.COMPLETED || status == SyncSessionStatus.PARTIAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SyncSession s)) return false;
        return Objects.equals(id, s.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "SyncSession{id=" + id + ", user=" + userId + ", status=" + status + ", ops=" + operationsApplied + "/" + operationsSubmitted + "}";
    }
}
