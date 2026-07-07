package com.yowyob.tiibntick.core.realtime.domain.model.enums;

/**
 * Lifecycle states of a WebSocket session.
 *
 * @author MANFOUO Braun
 */
public enum SessionStatus {

    /** Session is open and the client is actively sending/receiving messages. */
    ACTIVE,

    /** Session is open but no message received within the idle timeout period. */
    IDLE,

    /** Session timed out (heartbeat TTL expired). Will be cleaned up. */
    EXPIRED,

    /** Session was closed (by client DISCONNECT frame or network error). */
    DISCONNECTED
}
