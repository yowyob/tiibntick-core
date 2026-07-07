package com.yowyob.tiibntick.core.sync.domain.event;

import com.yowyob.tiibntick.core.sync.domain.model.enums.ConflictResolution;

public class SyncConflictDetectedEvent extends SyncDomainEvent {

    private static final String TOPIC = "tnt.sync.conflict.detected";

    private final String sessionId;
    private final String userId;
    private final String aggregateType;
    private final String aggregateId;
    private final ConflictResolution resolution;

    public SyncConflictDetectedEvent(String tenantId, String sessionId, String userId,
                                     String aggregateType, String aggregateId, ConflictResolution resolution) {
        super(tenantId);
        this.sessionId = sessionId;
        this.userId = userId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.resolution = resolution;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public ConflictResolution getResolution() { return resolution; }

    @Override
    public String kafkaTopic() { return TOPIC; }
}
