package com.yowyob.tiibntick.core.geo.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a RoadArc entity.
 *
 * Author: MANFOUO Braun
 */
public final class RoadArcId {

    private final String value;

    private RoadArcId(String value) {
        this.value = Objects.requireNonNull(value, "RoadArcId value must not be null").trim();
        if (this.value.isBlank()) {
            throw new IllegalArgumentException("RoadArcId must not be blank");
        }
    }

    public static RoadArcId of(String value) {
        return new RoadArcId(value);
    }

    public static RoadArcId generate() {
        return new RoadArcId(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoadArcId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "RoadArcId{" + value + "}";
    }
}
