package com.yowyob.tiibntick.core.realtime.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for actor presence management.
 * All properties namespaced under {@code tnt.realtime.presence}.
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.realtime.presence")
public class PresenceProperties {

    /**
     * Redis TTL for presence records.
     * If no keepalive arrives within this duration, Redis expires the record
     * and the actor is considered offline. Default: 30 seconds.
     */
    private int ttlSeconds = 30;

    /**
     * Duration without an update before an actor is considered stale
     * and eligible for the offline sweep. Default: 45 seconds.
     */
    private Duration staleDuration = Duration.ofSeconds(45);

    /**
     * Interval at which the presence sweep scheduler runs. Default: 60 seconds.
     */
    private Duration sweepInterval = Duration.ofSeconds(60);

    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    public Duration getStaleDuration() { return staleDuration; }
    public void setStaleDuration(Duration staleDuration) { this.staleDuration = staleDuration; }

    public Duration getSweepInterval() { return sweepInterval; }
    public void setSweepInterval(Duration sweepInterval) { this.sweepInterval = sweepInterval; }
}
