package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.YearMonth;
import java.util.Objects;

public final class PerformanceScore {

    private final int deliveryCount;
    private final double onTimeRate;
    private final double avgRating;
    private final YearMonth period;

    private PerformanceScore(int deliveryCount, double onTimeRate, double avgRating, YearMonth period) {
        if (deliveryCount < 0) {
            throw new IllegalArgumentException("deliveryCount must not be negative");
        }
        if (onTimeRate < 0.0 || onTimeRate > 1.0) {
            throw new IllegalArgumentException("onTimeRate must be between 0 and 1");
        }
        if (avgRating < 0.0 || avgRating > 5.0) {
            throw new IllegalArgumentException("avgRating must be between 0 and 5");
        }
        this.deliveryCount = deliveryCount;
        this.onTimeRate = onTimeRate;
        this.avgRating = avgRating;
        this.period = Objects.requireNonNull(period, "period must not be null");
    }

    public static PerformanceScore of(int deliveryCount, double onTimeRate, double avgRating, YearMonth period) {
        return new PerformanceScore(deliveryCount, onTimeRate, avgRating, period);
    }

    public int deliveryCount() {
        return deliveryCount;
    }

    public double onTimeRate() {
        return onTimeRate;
    }

    public double avgRating() {
        return avgRating;
    }

    public YearMonth period() {
        return period;
    }

    public double computeCompositeScore() {
        return (avgRating / 5.0 * 0.5) + (onTimeRate * 0.3) + (Math.min(deliveryCount / 100.0, 1.0) * 0.2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PerformanceScore other)) return false;
        return deliveryCount == other.deliveryCount
                && Double.compare(onTimeRate, other.onTimeRate) == 0
                && Double.compare(avgRating, other.avgRating) == 0
                && period.equals(other.period);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deliveryCount, onTimeRate, avgRating, period);
    }
}
