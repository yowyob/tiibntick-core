package com.yowyob.tiibntick.core.sync.domain.event;

import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;

public class EntityVersionChangedEvent extends SyncDomainEvent {

    private static final String TOPIC = "tnt.sync.entity.version.changed";

    private final String aggregateType;
    private final String aggregateId;
    private final DeltaOperation operation;
    private final String payloadJson;
    private final long version;

    public EntityVersionChangedEvent(String tenantId, String aggregateType, String aggregateId,
                                     DeltaOperation operation, String payloadJson, long version) {
        super(tenantId);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.operation = operation;
        this.payloadJson = payloadJson;
        this.version = version;
    }

    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public DeltaOperation getOperation() { return operation; }
    public String getPayloadJson() { return payloadJson; }
    public long getVersion() { return version; }

    @Override
    public String kafkaTopic() { return TOPIC; }
}
