package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import reactor.core.publisher.Mono;

/**
 * Use case for terminating a WebSocket session on client disconnect or server expiry.
 *
 * @author MANFOUO Braun
 */
public interface ITerminateSessionUseCase {

    /**
     * Terminates a session, cleans up subscriptions, and marks the actor offline
     * if they have no remaining active sessions.
     *
     * @param sessionId the session to terminate
     * @param reason    human-readable reason (e.g. "CLIENT_DISCONNECT", "TIMEOUT")
     * @return Mono completing when cleanup is done
     */
    Mono<Void> terminateSession(SessionId sessionId, String reason);
}
