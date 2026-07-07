package com.yowyob.tiibntick.core.geo.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a RoadNode aggregate.
 *
 * Author: MANFOUO Braun
 */
public final class RoadNodeId {

    private final String value;

    private RoadNodeId(String value) {
        this.value = Objects.requireNonNull(value, "RoadNodeId value must not be null").trim();
        if (this.value.isBlank()) {
            throw new IllegalArgumentException("RoadNodeId must not be blank");
        }
    }

    public static RoadNodeId of(String value) {
        return new RoadNodeId(value);
    }

    public static RoadNodeId generate() {
        return new RoadNodeId(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoadNodeId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "RoadNodeId{" + value + "}";
    }
}
