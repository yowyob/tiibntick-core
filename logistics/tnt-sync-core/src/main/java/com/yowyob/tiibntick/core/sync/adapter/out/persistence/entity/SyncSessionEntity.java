package com.yowyob.tiibntick.core.sync.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.sync.domain.model.SyncSession;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSessionId;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("tnt_sync_session")
public class SyncSessionEntity {

    @Id
    @Column("id")
    public String id;

    @Column("user_id")
    public String userId;

    @Column("tenant_id")
    public String tenantId;

    @Column("device_id")
    public String deviceId;

    @Column("since_token")
    public String sinceToken;

    @Column("since_sync_at")
    public LocalDateTime sinceSyncAt;

    @Column("started_at")
    public LocalDateTime startedAt;

    @Column("completed_at")
    public LocalDateTime completedAt;

    @Column("operations_submitted")
    public int operationsSubmitted;

    @Column("operations_applied")
    public int operationsApplied;

    @Column("conflicts_detected")
    public int conflictsDetected;

    @Column("conflicts_resolved")
    public int conflictsResolved;

    @Column("status")
    public String status;

    @Column("result_token")
    public String resultToken;

    public SyncSession toDomain() {
        var sinceT = new SyncToken(sinceToken, userId, tenantId, deviceId,
                sinceSyncAt != null ? sinceSyncAt : LocalDateTime.of(1970, 1, 1, 0, 0));
        var session = new SyncSession(SyncSessionId.of(id), userId, tenantId, deviceId, sinceT);
        session.recordPushStats(operationsSubmitted, operationsApplied, conflictsDetected, conflictsResolved);
        return session;
    }

    public static SyncSessionEntity fromDomain(SyncSession session) {
        var e = new SyncSessionEntity();
        e.id = session.getId().value();
        e.userId = session.getUserId();
        e.tenantId = session.getTenantId();
        e.deviceId = session.getDeviceId();
        e.sinceToken = session.getSinceToken().value();
        e.sinceSyncAt = session.getSinceToken().lastSyncAt();
        e.startedAt = session.getStartedAt();
        e.completedAt = session.getCompletedAt();
        e.operationsSubmitted = session.getOperationsSubmitted();
        e.operationsApplied = session.getOperationsApplied();
        e.conflictsDetected = session.getConflictsDetected();
        e.conflictsResolved = session.getConflictsResolved();
        e.status = session.getStatus().name();
        e.resultToken = session.getResultToken() != null ? session.getResultToken().value() : null;
        return e;
    }
}
