package com.yowyob.tiibntick.core.billing.templates.domain.model;

/**
 * Defines the data type of a template parameter value.
 *
 * <p>Used by the UI to render the correct input widget (slider for PERCENTAGE,
 * currency input for MONEY, toggle for BOOLEAN, etc.) and by the validation
 * service to enforce min/max constraints.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public enum ParameterType {

    /**
     * Monetary amount in XAF (Central African Franc).
     * Example: basePrice = 500 XAF
     */
    MONEY,

    /**
     * Percentage value between 0 and 100 (stored as decimal 0.0–1.0 internally).
     * Example: fragile_surcharge_pct = 20 (meaning 20%)
     */
    PERCENTAGE,

    /**
     * Whole number (integer) value.
     * Example: perishable_time_limit_hours = 4
     */
    INTEGER,

    /**
     * Decimal number (floating-point).
     * Example: terrainDegradationFactor = 1.25
     */
    DECIMAL,

    /**
     * Boolean flag (true/false).
     * Example: autoUpdateFuelPrice = true
     */
    BOOLEAN,

    /**
     * Time multiplier factor (≥ 1.0).
     * Example: night_multiplier = 1.5 (meaning 150% of base price)
     */
    MULTIPLIER
}
