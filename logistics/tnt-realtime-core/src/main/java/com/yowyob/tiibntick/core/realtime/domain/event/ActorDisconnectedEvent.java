package com.yowyob.tiibntick.core.realtime.domain.event;

/**
 * Emitted when an actor's WebSocket session is closed (DISCONNECT frame,
 * network error, or server-side expiry).
 * Triggers presence update to OFFLINE.
 *
 * @author MANFOUO Braun
 */
public class ActorDisconnectedEvent extends RealtimeDomainEvent {

    private static final String TOPIC = "tnt.realtime.actor.disconnected";

    private final String sessionId;
    private final String userId;
    private final String reason;

    public ActorDisconnectedEvent(String tenantId, String sessionId, String userId, String reason) {
        super(tenantId);
        this.sessionId = sessionId;
        this.userId = userId;
        this.reason = reason;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getReason() { return reason; }

    @Override
    public String kafkaTopic() {
        return TOPIC;
    }
}
