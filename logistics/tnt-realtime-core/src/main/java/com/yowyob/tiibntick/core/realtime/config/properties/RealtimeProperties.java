package com.yowyob.tiibntick.core.realtime.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Top-level configuration properties for the tnt-realtime-core module.
 * All properties are namespaced under {@code tnt.realtime}.
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.realtime")
public class RealtimeProperties {

    /** WebSocket endpoint path registered in the application. Default: /ws/realtime */
    private String websocketPath = "/ws/realtime";

    /** Maximum duration of session silence before the session is expired. Default: 60s */
    private Duration sessionIdleTimeout = Duration.ofSeconds(60);

    /** Interval at which the stale session sweep runs. Default: 30s */
    private Duration sessionSweepInterval = Duration.ofSeconds(30);

    /** Maximum number of topics a single session can subscribe to. Default: 50 */
    private int maxTopicsPerSession = 50;

    /** The instance identifier used for multi-instance routing. Default: local */
    private String instanceId = "local";

    /** Base URL of tnt-actor-core for location updates (HTTP mode). Default: empty (in-process) */
    private String actorCoreBaseUrl = "";

    /** Kafka topics configuration */
    private KafkaTopics kafka = new KafkaTopics();

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getWebsocketPath() { return websocketPath; }
    public void setWebsocketPath(String websocketPath) { this.websocketPath = websocketPath; }

    public Duration getSessionIdleTimeout() { return sessionIdleTimeout; }
    public void setSessionIdleTimeout(Duration sessionIdleTimeout) { this.sessionIdleTimeout = sessionIdleTimeout; }

    public Duration getSessionSweepInterval() { return sessionSweepInterval; }
    public void setSessionSweepInterval(Duration sessionSweepInterval) { this.sessionSweepInterval = sessionSweepInterval; }

    public int getMaxTopicsPerSession() { return maxTopicsPerSession; }
    public void setMaxTopicsPerSession(int maxTopicsPerSession) { this.maxTopicsPerSession = maxTopicsPerSession; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getActorCoreBaseUrl() { return actorCoreBaseUrl; }
    public void setActorCoreBaseUrl(String actorCoreBaseUrl) { this.actorCoreBaseUrl = actorCoreBaseUrl; }

    public KafkaTopics getKafka() { return kafka; }
    public void setKafka(KafkaTopics kafka) { this.kafka = kafka; }

    /**
     * Kafka topic name configuration for realtime events.
     */
    public static class KafkaTopics {
        private String etaUpdated = "tnt.route.eta.updated";
        private String missionStatusChanged = "tnt.delivery.mission.status.changed";
        private String actorConnected = "tnt.realtime.actor.connected";
        private String actorDisconnected = "tnt.realtime.actor.disconnected";
        private String gpsPositionUpdated = "tnt.realtime.gps.position.updated";
        private String geofenceTriggered = "tnt.realtime.geofence.triggered";
        private String etaUpdatePublished = "tnt.realtime.eta.updated";

        public String getEtaUpdated() { return etaUpdated; }
        public void setEtaUpdated(String etaUpdated) { this.etaUpdated = etaUpdated; }

        public String getMissionStatusChanged() { return missionStatusChanged; }
        public void setMissionStatusChanged(String v) { this.missionStatusChanged = v; }

        public String getActorConnected() { return actorConnected; }
        public void setActorConnected(String v) { this.actorConnected = v; }

        public String getActorDisconnected() { return actorDisconnected; }
        public void setActorDisconnected(String v) { this.actorDisconnected = v; }

        public String getGpsPositionUpdated() { return gpsPositionUpdated; }
        public void setGpsPositionUpdated(String v) { this.gpsPositionUpdated = v; }

        public String getGeofenceTriggered() { return geofenceTriggered; }
        public void setGeofenceTriggered(String v) { this.geofenceTriggered = v; }

        public String getEtaUpdatePublished() { return etaUpdatePublished; }
        public void setEtaUpdatePublished(String v) { this.etaUpdatePublished = v; }
    }
}
