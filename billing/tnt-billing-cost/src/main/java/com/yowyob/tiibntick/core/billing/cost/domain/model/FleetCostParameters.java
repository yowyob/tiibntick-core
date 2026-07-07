package com.yowyob.tiibntick.core.billing.cost.domain.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FleetCostParameters — fleet-specific operational cost calibration parameters.
 *
 * <p>Each FreelancerOrganization or Agency can have its own {@code FleetCostParameters},
 * overriding the global {@link CostParameters} for the cost computation engine.
 * This enables accurate per-actor cost models (e.g., a freelancer with an efficient
 * moto vs. an agency with an older van fleet).
 *
 * <p>When present in a {@link CostContext}, these parameters take precedence over
 * the global {@link CostParameters} fetched from tnt-settings-core.
 *
 * <p>Persisted in the {@code fleet_cost_parameters} table, keyed by {@code ownerOrgId}
 * (UUID of the FreelancerOrg or Agency).
 *
 * <p>Reference: {@code tiibntick(3).tex} — Section 3.5 "Paramètres de flotte spécifiques"
 * and {@code 03_TNT_BILLING_Ameliorations.md} — Section 7.1 FleetCostParameters.
 *
 * @author MANFOUO Braun
 */
@Builder
public record FleetCostParameters(
        /**
         * UUID of the owning FreelancerOrganization or Agency.
         * Used to look up these parameters per actor.
         * References tnt-organization-core UUID — pure integration key (no join).
         */
        String ownerOrgId,

        /**
         * Fuel price per liter in XAF.
         * Auto-updated if {@link #autoUpdateFuelPrice} is true.
         * Default: 700 XAF/L (Yaoundé 2025 moto fuel price).
         */
        BigDecimal fuelPriceLiterXAF,

        /**
         * Vehicle wear rate per km in XAF/km.
         * Represents maintenance amortization cost per km.
         * Default: 10 XAF/km.
         */
        BigDecimal vehicleWearRatePerKm,

        /**
         * Value of the deliverer's time per hour in XAF/h.
         * Represents the opportunity cost of the deliverer's time.
         * Default: 500 XAF/h (≈ 8.33 XAF/min).
         */
        BigDecimal timeValuePerHour,

        /**
         * Terrain degradation factor — multiplier for road degradation.
         * Range: 1.0 (good roads) to 2.0 (severe degradation).
         * Default: 1.0 (neutral, no extra degradation).
         */
        BigDecimal terrainDegradationFactor,

        /**
         * Rain penalty factor — multiplier applied during rain conditions.
         * Range: 1.0 (dry) to 1.5 (heavy rain).
         * Default: 1.1 (slight rain penalty).
         */
        BigDecimal rainPenaltyFactor,

        /**
         * Whether to auto-update {@link #fuelPriceLiterXAF} via fuel price API.
         * If true, the price is refreshed daily from the national fuel reference.
         */
        Boolean autoUpdateFuelPrice,

        /**
         * Timestamp of the last fuel price auto-update.
         * Null if never updated.
         */
        LocalDateTime lastFuelPriceUpdateAt
) {
    // ─── Validation ────────────────────────────────────────────────────────

    public FleetCostParameters {
        if (fuelPriceLiterXAF != null && fuelPriceLiterXAF.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fuelPriceLiterXAF must be positive, got: " + fuelPriceLiterXAF);
        }
        if (terrainDegradationFactor != null) {
            BigDecimal min = new BigDecimal("1.0");
            BigDecimal max = new BigDecimal("2.0");
            if (terrainDegradationFactor.compareTo(min) < 0 || terrainDegradationFactor.compareTo(max) > 0) {
                throw new IllegalArgumentException(
                        "terrainDegradationFactor must be in [1.0, 2.0], got: " + terrainDegradationFactor);
            }
        }
        if (rainPenaltyFactor != null) {
            BigDecimal min = new BigDecimal("1.0");
            BigDecimal max = new BigDecimal("1.5");
            if (rainPenaltyFactor.compareTo(min) < 0 || rainPenaltyFactor.compareTo(max) > 0) {
                throw new IllegalArgumentException(
                        "rainPenaltyFactor must be in [1.0, 1.5], got: " + rainPenaltyFactor);
            }
        }
    }

    // ─── Factory methods ───────────────────────────────────────────────────

    /**
     * Creates default FleetCostParameters for a Cameroonian moto (benskin) freelancer.
     *
     * @param ownerOrgId the FreelancerOrg or Agency UUID
     * @return default parameters
     */
    public static FleetCostParameters defaultForMotoFreelancer(String ownerOrgId) {
        return FleetCostParameters.builder()
                .ownerOrgId(ownerOrgId)
                .fuelPriceLiterXAF(new BigDecimal("700"))
                .vehicleWearRatePerKm(new BigDecimal("10"))
                .timeValuePerHour(new BigDecimal("500"))
                .terrainDegradationFactor(new BigDecimal("1.0"))
                .rainPenaltyFactor(new BigDecimal("1.1"))
                .autoUpdateFuelPrice(false)
                .lastFuelPriceUpdateAt(null)
                .build();
    }

    /**
     * Creates default FleetCostParameters for a light van freelancer.
     *
     * @param ownerOrgId the FreelancerOrg UUID
     * @return default parameters for van
     */
    public static FleetCostParameters defaultForVanFreelancer(String ownerOrgId) {
        return FleetCostParameters.builder()
                .ownerOrgId(ownerOrgId)
                .fuelPriceLiterXAF(new BigDecimal("760"))  // diesel price
                .vehicleWearRatePerKm(new BigDecimal("18"))
                .timeValuePerHour(new BigDecimal("700"))
                .terrainDegradationFactor(new BigDecimal("1.0"))
                .rainPenaltyFactor(new BigDecimal("1.05"))
                .autoUpdateFuelPrice(false)
                .lastFuelPriceUpdateAt(null)
                .build();
    }

    // ─── Convenience converters ────────────────────────────────────────────

    /**
     * Converts the time value per hour to per-minute rate (for CostParameters compatibility).
     *
     * @return time value in XAF/min, or null if not set
     */
    public BigDecimal timeValuePerMinute() {
        if (timeValuePerHour == null) return null;
        return timeValuePerHour.divide(new BigDecimal("60"), 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Returns the effective fuel price, or the Cameroon default (700 XAF/L) if not set.
     */
    public BigDecimal effectiveFuelPriceLiterXAF() {
        return fuelPriceLiterXAF != null ? fuelPriceLiterXAF : new BigDecimal("700");
    }

    /**
     * Returns the effective terrain degradation factor (defaults to 1.0 if not set).
     */
    public double effectiveTerrainFactor() {
        return terrainDegradationFactor != null ? terrainDegradationFactor.doubleValue() : 1.0;
    }

    /**
     * Returns the effective rain penalty factor (defaults to 1.1 if not set).
     */
    public double effectiveRainPenaltyFactor() {
        return rainPenaltyFactor != null ? rainPenaltyFactor.doubleValue() : 1.1;
    }
}
