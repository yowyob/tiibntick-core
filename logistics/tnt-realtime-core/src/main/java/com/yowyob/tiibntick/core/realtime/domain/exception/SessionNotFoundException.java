package com.yowyob.tiibntick.core.realtime.domain.exception;

/**
 * Thrown when a WebSocket session cannot be found by its identifier.
 *
 * @author MANFOUO Braun
 */
public class SessionNotFoundException extends RealtimeException {

    public SessionNotFoundException(String sessionId) {
        super("REALTIME_SESSION_NOT_FOUND",
              "WebSocket session not found: " + sessionId);
    }
}
