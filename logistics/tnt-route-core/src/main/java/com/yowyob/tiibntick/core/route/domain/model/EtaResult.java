package com.yowyob.tiibntick.core.route.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record EtaResult(
        Instant expected,
        Instant lowerBound,
        Instant upperBound,
        double confidenceLevel,
        Instant computedAt
) {
    public EtaResult {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(lowerBound);
        Objects.requireNonNull(upperBound);
        Objects.requireNonNull(computedAt);
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
