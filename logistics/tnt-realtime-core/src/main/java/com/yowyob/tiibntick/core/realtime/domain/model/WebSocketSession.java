package com.yowyob.tiibntick.core.realtime.domain.model;

import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.SessionStatus;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain entity representing an active WebSocket session.
 * A session belongs to exactly one user and one tenant.
 * It maintains the set of topics the client has subscribed to.
 *
 * <p>This is the domain representation — not Spring's WebSocketSession.
 * The Spring infrastructure object lives only in the adapter layer.</p>
 *
 * @author MANFOUO Braun
 */
@Getter
public class WebSocketSession {

    private final SessionId id;
    private final String userId;
    private final String tenantId;
    private final DeviceType deviceType;
    private final DeviceInfo deviceInfo;
    private final String remoteAddress;
    private final LocalDateTime connectedAt;

    private final Set<String> subscriptions;
    private LocalDateTime lastHeartbeatAt;
    private SessionStatus status;

    private WebSocketSession(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Session id must not be null");
        this.userId = Objects.requireNonNull(builder.userId, "userId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.deviceType = Objects.requireNonNull(builder.deviceType, "deviceType must not be null");
        this.deviceInfo = builder.deviceInfo;
        this.remoteAddress = builder.remoteAddress;
        this.connectedAt = Objects.requireNonNull(builder.connectedAt, "connectedAt must not be null");
        this.subscriptions = new HashSet<>(builder.subscriptions);
        this.lastHeartbeatAt = connectedAt;
        this.status = SessionStatus.ACTIVE;
    }

    /**
     * Subscribes this session to a topic, making it eligible
     * to receive broadcasts sent to that topic.
     *
     * @param topic the STOMP topic path (e.g. /topic/delivery/MISSION-123)
     */
    public void subscribe(String topic) {
        Objects.requireNonNull(topic, "Topic must not be null");
        if (!topic.startsWith("/topic/")) {
            throw new IllegalArgumentException("Invalid topic path: must start with /topic/");
        }
        subscriptions.add(topic);
    }

    /**
     * Unsubscribes this session from a topic.
     *
     * @param topic the STOMP topic path
     */
    public void unsubscribe(String topic) {
        subscriptions.remove(topic);
    }

    /**
     * Updates the last heartbeat timestamp, keeping the session alive.
     * Called whenever a STOMP heartbeat or any message is received.
     */
    public void touch() {
        this.lastHeartbeatAt = LocalDateTime.now();
        if (this.status == SessionStatus.IDLE) {
            this.status = SessionStatus.ACTIVE;
        }
    }

    /**
     * Determines whether this session is still alive relative to the
     * current instant, given a maximum allowed silence duration.
     *
     * @param now              the current timestamp
     * @param maxSilencePeriod maximum duration without a heartbeat before considered dead
     * @return true if the session is still alive
     */
    public boolean isAlive(LocalDateTime now, Duration maxSilencePeriod) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(maxSilencePeriod, "maxSilencePeriod must not be null");
        return status != SessionStatus.EXPIRED
                && status != SessionStatus.DISCONNECTED
                && lastHeartbeatAt.plus(maxSilencePeriod).isAfter(now);
    }

    /**
     * Marks this session as idle (still open but no recent message).
     */
    public void markIdle() {
        if (this.status == SessionStatus.ACTIVE) {
            this.status = SessionStatus.IDLE;
        }
    }

    /**
     * Marks this session as disconnected (closed by client or server).
     */
    public void markDisconnected() {
        this.status = SessionStatus.DISCONNECTED;
    }

    /**
     * Marks this session as expired (timed out).
     */
    public void markExpired() {
        this.status = SessionStatus.EXPIRED;
    }

    /**
     * Returns an unmodifiable view of the subscription set.
     *
     * @return immutable set of subscribed topic paths
     */
    public Set<String> getSubscriptions() {
        return Collections.unmodifiableSet(subscriptions);
    }

    public boolean isSubscribedTo(String topic) {
        return subscriptions.contains(topic);
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE || status == SessionStatus.IDLE;
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private SessionId id;
        private String userId;
        private String tenantId;
        private DeviceType deviceType = DeviceType.PWA_BROWSER;
        private DeviceInfo deviceInfo;
        private String remoteAddress;
        private LocalDateTime connectedAt = LocalDateTime.now();
        private final Set<String> subscriptions = new HashSet<>();

        public Builder id(SessionId id) { this.id = id; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder deviceType(DeviceType deviceType) { this.deviceType = deviceType; return this; }
        public Builder deviceInfo(DeviceInfo deviceInfo) { this.deviceInfo = deviceInfo; return this; }
        public Builder remoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; return this; }
        public Builder connectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; return this; }

        public WebSocketSession build() {
            return new WebSocketSession(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebSocketSession s)) return false;
        return Objects.equals(id, s.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WebSocketSession{id=" + id + ", userId=" + userId + ", tenant=" + tenantId + ", status=" + status + "}";
    }
}
