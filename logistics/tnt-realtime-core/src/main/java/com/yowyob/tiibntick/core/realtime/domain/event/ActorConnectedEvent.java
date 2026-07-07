package com.yowyob.tiibntick.core.realtime.domain.event;

import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;

/**
 * Emitted when an actor establishes a WebSocket connection.
 * Consumed by presence management and analytics services.
 *
 * @author MANFOUO Braun
 */
public class ActorConnectedEvent extends RealtimeDomainEvent {

    private static final String TOPIC = "tnt.realtime.actor.connected";

    private final String sessionId;
    private final String userId;
    private final DeviceType deviceType;
    private final String remoteAddress;

    public ActorConnectedEvent(String tenantId, String sessionId, String userId,
                               DeviceType deviceType, String remoteAddress) {
        super(tenantId);
        this.sessionId = sessionId;
        this.userId = userId;
        this.deviceType = deviceType;
        this.remoteAddress = remoteAddress;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public DeviceType getDeviceType() { return deviceType; }
    public String getRemoteAddress() { return remoteAddress; }

    @Override
    public String kafkaTopic() {
        return TOPIC;
    }
}
