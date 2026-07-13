package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.UUID;

/**
 * Value Object — Strongly-typed identifier for Review.
 * @author MANFOUO Braun
 */
public record ReviewId(UUID value) {

    public static ReviewId generate() {
        return new ReviewId(UUID.randomUUID());
    }

    public static ReviewId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("ReviewId value must not be null");
        return new ReviewId(value);
    }

    public static ReviewId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
