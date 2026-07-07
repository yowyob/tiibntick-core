package com.yowyob.tiibntick.core.sync.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class SyncDomainEvent {
    private final String eventId = UUID.randomUUID().toString();
    private final String tenantId;
    private final LocalDateTime occurredAt = LocalDateTime.now();
    private final String sourceModule = "tnt-sync-core";

    protected SyncDomainEvent(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getEventId() { return eventId; }
    public String getTenantId() { return tenantId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getSourceModule() { return sourceModule; }

    public abstract String kafkaTopic();
}
