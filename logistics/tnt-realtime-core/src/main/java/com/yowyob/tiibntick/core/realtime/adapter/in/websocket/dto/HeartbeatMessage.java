package com.yowyob.tiibntick.core.realtime.adapter.in.websocket.dto;

/**
 * Heartbeat acknowledgment payload sent by the client to keep the session alive.
 * Destination: {@code /app/heartbeat}
 *
 * @author MANFOUO Braun
 */
public record HeartbeatMessage(long clientTimestamp) {}
