package com.yowyob.tiibntick.core.actor.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class ActorId {

    private final UUID value;

    private ActorId(UUID value) {
        this.value = Objects.requireNonNull(value, "ActorId value must not be null");
    }

    public static ActorId of(UUID value) {
        return new ActorId(value);
    }

    public static ActorId generate() {
        return new ActorId(UUID.randomUUID());
    }

    public static ActorId from(String value) {
        return new ActorId(UUID.fromString(Objects.requireNonNull(value, "ActorId string value must not be null")));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorId other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
