package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Value Object — composite rating left by a client for a provider.
 * Each sub-score is 1-5.
 * @author MANFOUO Braun
 */
public record Rating(
        int overall,
        int punctuality,
        int communication,
        int packaging,
        int value
) {
    public double average() {
        return (overall + punctuality + communication + packaging + value) / 5.0;
    }

    public boolean isValid() {
        return inRange(overall) && inRange(punctuality)
                && inRange(communication) && inRange(packaging) && inRange(value);
    }

    private boolean inRange(int v) { return v >= 1 && v <= 5; }
}
