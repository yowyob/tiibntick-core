package com.yowyob.tiibntick.core.trust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration Properties — {@code TntTrustProperties}.
 *
 * <p>Binds all {@code tnt.trust.*} properties from the host application's
 * {@code application.yml} (or environment variables) into a typed bean.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@ConfigurationProperties(prefix = "tnt.trust")
public class TntTrustProperties {

    /**
     * Base URL of the yow-trust-event internal REST API.
     * Default: {@code http://yow-trust-event:8085}
     */
    private String trustEventBaseUrl = "http://yow-trust-event:8085";

    /**
     * Kafka topic for publishing logistic trust events.
     * Must match the topic consumed by yow-trust-event.
     * Default: {@code yow.trust.events}
     */
    private String kafkaTrustEventsTopic = "yow.trust.events";

    /**
     * Kafka topic for receiving committed event notifications from yow-trust-event.
     * Default: {@code yow.trust.events.committed}
     */
    private String kafkaCommittedTopic = "yow.trust.events.committed";

    /**
     * Default Fabric channel name used for logistic event anchoring.
     * Passed along in the Kafka message for routing by yow-trust-event.
     */
    private String fabricDefaultChannel = "yowyob-logistics-channel";

    /**
     * WebClient connection timeout (ms) for Trust Event REST calls.
     */
    private int restClientTimeoutMs = 10_000;

    /**
     * Deployment-time toggle for the whole {@code tnt-trust-core} module (§15.1 of
     * {@code TNT_CORE_Connexion_Trust_Module.md}). {@code false} in environments
     * without a Kernel Trust stack — no {@code tnt-trust-core} bean is wired at all.
     */
    private boolean enabled = true;

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public String getTrustEventBaseUrl() { return trustEventBaseUrl; }
    public void setTrustEventBaseUrl(String v) { this.trustEventBaseUrl = v; }
    public String getKafkaTrustEventsTopic() { return kafkaTrustEventsTopic; }
    public void setKafkaTrustEventsTopic(String v) { this.kafkaTrustEventsTopic = v; }
    public String getKafkaCommittedTopic() { return kafkaCommittedTopic; }
    public void setKafkaCommittedTopic(String v) { this.kafkaCommittedTopic = v; }
    public String getFabricDefaultChannel() { return fabricDefaultChannel; }
    public void setFabricDefaultChannel(String v) { this.fabricDefaultChannel = v; }
    public int getRestClientTimeoutMs() { return restClientTimeoutMs; }
    public void setRestClientTimeoutMs(int v) { this.restClientTimeoutMs = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
}
