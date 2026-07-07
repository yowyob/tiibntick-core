package com.yowyob.tiibntick.core.realtime.domain.model;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Objects;

/**
 * ETA confidence interval produced by the Kalman filter in tnt-route-core.
 * Represents the probabilistic range [lowerBound, upperBound] within which
 * the deliverer is expected to arrive (95% confidence level).
 *
 * @author MANFOUO Braun
 */
public record ETAInterval(
        LocalDateTime lowerBound,
        LocalDateTime upperBound,
        double confidenceLevel
) {

    public ETAInterval {
        Objects.requireNonNull(lowerBound, "Lower bound must not be null");
        Objects.requireNonNull(upperBound, "Upper bound must not be null");
        if (lowerBound.isAfter(upperBound)) {
            throw new IllegalArgumentException("Lower bound must be before upper bound");
        }
        if (confidenceLevel < 0.0 || confidenceLevel > 1.0) {
            throw new IllegalArgumentException("Confidence level must be in [0,1]");
        }
    }

    /**
     * Creates an ETA interval with default 95% confidence.
     *
     * @param lowerBound earliest expected arrival
     * @param upperBound latest expected arrival
     * @return new ETAInterval instance
     */
    public static ETAInterval of(LocalDateTime lowerBound, LocalDateTime upperBound) {
        return new ETAInterval(lowerBound, upperBound, 0.95);
    }

    public static ETAInterval of(LocalDateTime lowerBound, LocalDateTime upperBound, double confidence) {
        return new ETAInterval(lowerBound, upperBound, confidence);
    }

    /**
     * Returns the midpoint of this interval as the best single ETA estimate.
     *
     * @return best single ETA estimate (midpoint of the interval)
     */
    public LocalDateTime midpoint() {
        long halfSeconds = Duration.between(lowerBound, upperBound).toSeconds() / 2;
        return lowerBound.plusSeconds(halfSeconds);
    }

    /**
     * Returns the total width of this interval in minutes.
     *
     * @return interval width in minutes
     */
    public long widthMinutes() {
        return Duration.between(lowerBound, upperBound).toMinutes();
    }
}
