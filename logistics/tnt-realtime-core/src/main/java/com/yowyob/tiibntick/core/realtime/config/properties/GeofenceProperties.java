package com.yowyob.tiibntick.core.realtime.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the geofence monitoring system.
 * All properties namespaced under {@code tnt.realtime.geofence}.
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.realtime.geofence")
public class GeofenceProperties {

    /**
     * Whether geofence monitoring is enabled globally. Default: true.
     */
    private boolean enabled = true;

    /**
     * Default radius in meters for auto-created relay hub geofence zones. Default: 300m.
     */
    private double defaultRelayHubRadiusMeters = 300.0;

    /**
     * Default radius in meters for delivery address proximity zones. Default: 150m.
     */
    private double defaultDeliveryAddressRadiusMeters = 150.0;

    /**
     * Maximum number of active geofence zones allowed per tenant. Default: 500.
     */
    private int maxZonesPerTenant = 500;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getDefaultRelayHubRadiusMeters() { return defaultRelayHubRadiusMeters; }
    public void setDefaultRelayHubRadiusMeters(double v) { this.defaultRelayHubRadiusMeters = v; }

    public double getDefaultDeliveryAddressRadiusMeters() { return defaultDeliveryAddressRadiusMeters; }
    public void setDefaultDeliveryAddressRadiusMeters(double v) { this.defaultDeliveryAddressRadiusMeters = v; }

    public int getMaxZonesPerTenant() { return maxZonesPerTenant; }
    public void setMaxZonesPerTenant(int maxZonesPerTenant) { this.maxZonesPerTenant = maxZonesPerTenant; }
}
