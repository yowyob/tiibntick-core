package com.yowyob.tiibntick.core.billing.dsl.domain.model;

/**
 * Weather condition type used as the {@code weather} DSL variable.
 * Corresponds to the stochastic weather surcharge component in the
 * multi-criteria cost function: delta * xi_t(a).
 *
 * @author MANFOUO Braun
 */
public enum WeatherCondition {

    CLEAR,
    CLOUDY,
    RAIN_LIGHT,
    RAIN_HEAVY,
    STORM,
    FOG,
    HARMATTAN
}
