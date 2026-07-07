package com.yowyob.tiibntick.core.actor.domain.model;

public enum ActorStatus {

    ACTIVE,
    INACTIVE,
    SUSPENDED,
    BANNED;

    public static ActorStatus from(String value) {
        if (value == null || value.isBlank()) {
            return INACTIVE;
        }
        return valueOf(value.trim().toUpperCase());
    }

    public boolean isOperational() {
        return this == ACTIVE;
    }
}
