package com.yowyob.tiibntick.core.realtime.adapter.in.websocket.stomp;

/**
 * STOMP protocol commands (client-to-server and server-to-client).
 *
 * @author MANFOUO Braun
 */
public enum StompCommand {

    // ── Client → Server ────────────────────────────────────────────────────────
    CONNECT,
    SUBSCRIBE,
    UNSUBSCRIBE,
    SEND,
    DISCONNECT,
    ACK,
    NACK,

    // ── Server → Client ────────────────────────────────────────────────────────
    CONNECTED,
    MESSAGE,
    ERROR,
    RECEIPT,

    // ── Internal placeholder ───────────────────────────────────────────────────
    HEARTBEAT,
    UNKNOWN
}
