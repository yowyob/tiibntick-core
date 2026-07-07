package com.yowyob.tiibntick.core.sync.domain.event;

public class SyncCompletedEvent extends SyncDomainEvent {

    private static final String TOPIC = "tnt.sync.completed";

    private final String sessionId;
    private final String userId;
    private final String deviceId;
    private final int operationsApplied;
    private final int conflictsResolved;
    private final int deltaRecordsSent;
    private final String newSyncToken;

    public SyncCompletedEvent(String tenantId, String sessionId, String userId, String deviceId,
                              int operationsApplied, int conflictsResolved, int deltaRecordsSent,
                              String newSyncToken) {
        super(tenantId);
        this.sessionId = sessionId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.operationsApplied = operationsApplied;
        this.conflictsResolved = conflictsResolved;
        this.deltaRecordsSent = deltaRecordsSent;
        this.newSyncToken = newSyncToken;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getDeviceId() { return deviceId; }
    public int getOperationsApplied() { return operationsApplied; }
    public int getConflictsResolved() { return conflictsResolved; }
    public int getDeltaRecordsSent() { return deltaRecordsSent; }
    public String getNewSyncToken() { return newSyncToken; }

    @Override
    public String kafkaTopic() { return TOPIC; }
}
