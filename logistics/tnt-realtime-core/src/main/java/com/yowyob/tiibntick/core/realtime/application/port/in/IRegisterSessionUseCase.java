package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import reactor.core.publisher.Mono;

/**
 * Use case for registering a new WebSocket session upon client connection.
 *
 * @author MANFOUO Braun
 */
public interface IRegisterSessionUseCase {

    /**
     * Registers a new WebSocket session and marks the actor as online.
     *
     * @param sessionId     the unique session identifier (assigned by WebSocketHandler)
     * @param userId        the authenticated user ID (from JWT)
     * @param tenantId      the tenant context
     * @param deviceType    the connecting device type
     * @param deviceInfo    full device metadata
     * @param remoteAddress the client's IP address
     * @return Mono completing when the session is registered and presence updated
     */
    Mono<Void> registerSession(SessionId sessionId, String userId, String tenantId,
                               DeviceType deviceType, DeviceInfo deviceInfo, String remoteAddress);
}
