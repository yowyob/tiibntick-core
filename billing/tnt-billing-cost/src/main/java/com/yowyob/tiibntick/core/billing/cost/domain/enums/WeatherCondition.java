package com.yowyob.tiibntick.core.billing.cost.domain.enums;

/**
 * Weather condition affecting the arc cost.
 * Maps to the stochastic weather factor δ in the mathematical model.
 * Reference: tiibntick(3).tex — Section "Risque Météorologique"
 *
 * @author MANFOUO Braun
 */
public enum WeatherCondition {
    /** Clear weather — no surcharge. Factor = 1.0 */
    CLEAR(1.0),
    /** Cloudy — marginal increase. Factor = 1.05 */
    CLOUDY(1.05),
    /** Light rain — moderate surcharge. Factor = 1.20 */
    LIGHT_RAIN(1.20),
    /** Heavy rain — significant surcharge (ρ_pluie). Factor = 1.40 */
    HEAVY_RAIN(1.40),
    /** Flood conditions — extreme surcharge. Factor = 2.0 */
    FLOOD(2.0),
    /** Unknown — defaults to clear. */
    UNKNOWN(1.0);

    private final double wearFactor;

    WeatherCondition(double wearFactor) {
        this.wearFactor = wearFactor;
    }

    /** δ — multiplicative wear factor for this weather condition. */
    public double wearFactor() {
        return wearFactor;
    }

    /** Probability of rain (p_pluie) used in the weather surcharge formula. */
    public double rainProbability() {
        return switch (this) {
            case CLEAR -> 0.0;
            case CLOUDY -> 0.10;
            case LIGHT_RAIN -> 0.60;
            case HEAVY_RAIN -> 0.90;
            case FLOOD -> 1.0;
            default -> 0.0;
        };
    }
}
