package com.yowyob.tiibntick.core.sync.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tnt.sync")
public class SyncProperties {

    private int maxRecordsPerDelta = 500;
    private Duration tokenMaxAge = Duration.ofDays(7);
    private Duration sessionRetention = Duration.ofDays(30);
    private Duration sessionCleanupInterval = Duration.ofHours(6);
    private int maxOperationsPerBatch = 200;
    private boolean enabled = true;

    public int getMaxRecordsPerDelta() { return maxRecordsPerDelta; }
    public void setMaxRecordsPerDelta(int v) { this.maxRecordsPerDelta = v; }

    public Duration getTokenMaxAge() { return tokenMaxAge; }
    public void setTokenMaxAge(Duration v) { this.tokenMaxAge = v; }

    public Duration getSessionRetention() { return sessionRetention; }
    public void setSessionRetention(Duration v) { this.sessionRetention = v; }

    public Duration getSessionCleanupInterval() { return sessionCleanupInterval; }
    public void setSessionCleanupInterval(Duration v) { this.sessionCleanupInterval = v; }

    public int getMaxOperationsPerBatch() { return maxOperationsPerBatch; }
    public void setMaxOperationsPerBatch(int v) { this.maxOperationsPerBatch = v; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
