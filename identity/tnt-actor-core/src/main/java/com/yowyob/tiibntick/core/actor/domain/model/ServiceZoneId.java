package com.yowyob.tiibntick.core.actor.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class ServiceZoneId {

    private final UUID value;

    private ServiceZoneId(UUID value) {
        this.value = Objects.requireNonNull(value, "ServiceZoneId value must not be null");
    }

    public static ServiceZoneId of(UUID value) {
        return new ServiceZoneId(value);
    }

    public static ServiceZoneId generate() {
        return new ServiceZoneId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceZoneId other)) return false;
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
