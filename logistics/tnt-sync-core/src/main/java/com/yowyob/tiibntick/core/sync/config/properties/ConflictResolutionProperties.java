package com.yowyob.tiibntick.core.sync.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tnt.sync.conflict")
public class ConflictResolutionProperties {

    private String defaultStrategy = "LWW";
    private int maxRetries = 3;
    private boolean enableVectorClocks = true;

    public String getDefaultStrategy() { return defaultStrategy; }
    public void setDefaultStrategy(String v) { this.defaultStrategy = v; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int v) { this.maxRetries = v; }

    public boolean isEnableVectorClocks() { return enableVectorClocks; }
    public void setEnableVectorClocks(boolean v) { this.enableVectorClocks = v; }
}
