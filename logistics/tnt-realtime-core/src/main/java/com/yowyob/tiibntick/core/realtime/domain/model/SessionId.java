package com.yowyob.tiibntick.core.realtime.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a WebSocket session.
 * Wraps a UUID string to prevent primitive obsession.
 *
 * @author MANFOUO Braun
 */
public record SessionId(String value) {

    public SessionId {
        Objects.requireNonNull(value, "SessionId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SessionId value must not be blank");
        }
    }

    /**
     * Generates a new random SessionId.
     *
     * @return a new unique session identifier
     */
    public static SessionId generate() {
        return new SessionId(UUID.randomUUID().toString());
    }

    /**
     * Creates a SessionId from an existing string value.
     *
     * @param value the raw identifier string
     * @return a SessionId wrapping the provided value
     */
    public static SessionId of(String value) {
        return new SessionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
