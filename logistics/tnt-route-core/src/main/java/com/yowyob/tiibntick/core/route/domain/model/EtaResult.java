package com.yowyob.tiibntick.core.route.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record EtaResult(
        Instant expected,
        Instant lowerBound,
        Instant upperBound,
        double confidenceLevel,
        Instant computedAt,
        double remainingDistanceKm
) {
    public EtaResult {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(lowerBound);
        Objects.requireNonNull(upperBound);
        Objects.requireNonNull(computedAt);
        if (remainingDistanceKm < 0) {
            throw new IllegalArgumentException("remainingDistanceKm must be >= 0");
        }
    }

    public Duration range() {
        return Duration.between(lowerBound, upperBound);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(upperBound);
    }

    public long remainingMinutes(Instant now) {
        return Duration.between(now, expected).toMinutes();
    }
}
