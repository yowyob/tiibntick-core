package com.yowyob.tiibntick.core.geo.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of weather conditions at a given location and time.
 * The field {@code phiRain} (φ) is the dimensionless weather penalty factor
 * used in the 5D cost function: delta * xi_tilde.
 *
 * phi_rain calibration (domain expertise, West Africa):
 *   - No rain:          phi = 0.0
 *   - Light drizzle:    phi = 0.1  (precip < 2 mm/h)
 *   - Moderate rain:    phi = 0.3  (precip 2–10 mm/h)
 *   - Heavy rain:       phi = 0.6  (precip > 10 mm/h — dangerous roads)
 *
 * Author: MANFOUO Braun
 */
public final class WeatherCondition {

    private static final double PHI_NO_RAIN = 0.0;
    private static final double PHI_LIGHT = 0.1;
    private static final double PHI_MODERATE = 0.3;
    private static final double PHI_HEAVY = 0.6;

    private final boolean isRaining;
    private final double precipitationMmPerHour;
    private final double windSpeedKmh;
    private final double phiRain;
    private final Instant observedAt;

    private WeatherCondition(boolean isRaining, double precipitationMmPerHour,
                             double windSpeedKmh, double phiRain, Instant observedAt) {
        if (precipitationMmPerHour < 0) {
            throw new IllegalArgumentException("precipitationMmPerHour must be >= 0");
        }
        if (windSpeedKmh < 0) {
            throw new IllegalArgumentException("windSpeedKmh must be >= 0");
        }
        if (phiRain < 0 || phiRain > 1.0) {
            throw new IllegalArgumentException("phiRain must be in [0, 1], got: " + phiRain);
        }
        this.isRaining = isRaining;
        this.precipitationMmPerHour = precipitationMmPerHour;
        this.windSpeedKmh = windSpeedKmh;
        this.phiRain = phiRain;
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
    }

    public static WeatherCondition of(double precipitationMmPerHour, double windSpeedKmh, Instant observedAt) {
        boolean raining = precipitationMmPerHour > 0;
        double phi = computePhiRain(precipitationMmPerHour);
        return new WeatherCondition(raining, precipitationMmPerHour, windSpeedKmh, phi, observedAt);
    }

    public static WeatherCondition clear(Instant observedAt) {
        return new WeatherCondition(false, 0.0, 0.0, PHI_NO_RAIN, observedAt);
    }

    private static double computePhiRain(double precipMm) {
        if (precipMm <= 0) return PHI_NO_RAIN;
        if (precipMm < 2.0) return PHI_LIGHT;
        if (precipMm < 10.0) return PHI_MODERATE;
        return PHI_HEAVY;
    }

    public boolean isRaining() {
        return isRaining;
    }

    public double precipitationMmPerHour() {
        return precipitationMmPerHour;
    }

    public double windSpeedKmh() {
        return windSpeedKmh;
    }

    /**
     * The normalised weather penalty factor phi ∈ [0.0, 0.6] used in the 5D cost function.
     */
    public double phiRain() {
        return phiRain;
    }

    public Instant observedAt() {
        return observedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeatherCondition that)) return false;
        return isRaining == that.isRaining
                && Double.compare(that.precipitationMmPerHour, precipitationMmPerHour) == 0
                && Double.compare(that.phiRain, phiRain) == 0
                && observedAt.equals(that.observedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isRaining, precipitationMmPerHour, phiRain, observedAt);
    }

    @Override
    public String toString() {
        return "WeatherCondition{raining=" + isRaining + ", precip=" + precipitationMmPerHour
                + "mm/h, phi=" + phiRain + "}";
    }
}
