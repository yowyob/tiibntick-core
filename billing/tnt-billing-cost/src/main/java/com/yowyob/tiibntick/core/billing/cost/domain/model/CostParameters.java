package com.yowyob.tiibntick.core.billing.cost.domain.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * CostParameters — tenant-configurable parameters for the operational cost formula.
 *
 * All parameters are stored in tnt-settings-core and fetched per tenant.
 * Default values are calibrated for the Cameroonian market (Yaoundé/Douala, 2025).
 *
 * Reference formulas (tiibntick(3).tex):
 *   c_fuel(a) = d(a) × [κ_carb × p_essence + κ_usure × u_terrain(a)]
 *   c_wear(a) = d(a) × κ_usure × ρ(a) × δ(weather)
 *   c_time    = duration_min × v_temps
 *   c_penil   = ρ_index × penalty_base
 *   c_weather = p_rain × φ_rain
 *
 * @author MANFOUO Braun
 */
@Builder
public record CostParameters(
        /**
         * p_essence — fuel price per litre in the wallet currency.
         * Default: 730 XAF/L (Yaoundé 2025, moto fuel).
         */
        BigDecimal fuelPricePerLitre,
        /**
         * κ_carb — base fuel consumption in L/100km (vehicle-type dependent).
         * Default: 2.5 L/100km for motorcycle.
         */
        BigDecimal fuelConsumptionL100km,
        /**
         * κ_usure — base mechanical wear cost rate in XAF/km.
         * Represents maintenance cost per km on a standard road.
         * Default: 50 XAF/km for motorcycle.
         */
        BigDecimal vehicleWearCostPerKm,
        /**
         * v_temps — value of the driver's time in XAF/min.
         * Represents the opportunity cost of the driver's time.
         * Default: 15 XAF/min (≈ 900 XAF/h).
         */
        BigDecimal driverTimeValueXAFPerMin,
        /**
         * penalty_base — base penibility cost in XAF applied once per degraded road segment.
         * Default: 200 XAF.
         */
        BigDecimal penibilityBaseCostXAF,
        /**
         * φ_rain — surcharge cost applied when it rains (per delivery, not per km).
         * Default: 300 XAF for light rain, 500 XAF for heavy rain.
         * This field holds the light-rain value; heavy rain applies a multiplier.
         */
        BigDecimal rainSurchargeCostXAF,
        /**
         * φ_flood — surcharge cost applied during flood conditions.
         * Default: 1000 XAF.
         */
        BigDecimal floodSurchargeCostXAF,
        /**
         * Load factor sensitivity — how much extra fuel consumption per unit load.
         * Formula: effective_consumption = base × (1 + loadSensitivity × loadFactor)
         * Default: 0.20 (20% more fuel when fully loaded).
         */
        BigDecimal loadSensitivity,
        /** Wallet currency for this tenant. */
        Currency currency
) {
    public CostParameters {
        if (fuelPricePerLitre == null || fuelPricePerLitre.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("fuelPricePerLitre must be positive");
        if (currency == null) currency = Currency.getInstance("XAF");
    }

    /**
     * Returns default CostParameters calibrated for Cameroon (motorcycle, Yaoundé 2025).
     */
    public static CostParameters defaultForCameroon() {
        return CostParameters.builder()
                .fuelPricePerLitre(new BigDecimal("730"))
                .fuelConsumptionL100km(new BigDecimal("2.5"))
                .vehicleWearCostPerKm(new BigDecimal("50"))
                .driverTimeValueXAFPerMin(new BigDecimal("15"))
                .penibilityBaseCostXAF(new BigDecimal("200"))
                .rainSurchargeCostXAF(new BigDecimal("300"))
                .floodSurchargeCostXAF(new BigDecimal("1000"))
                .loadSensitivity(new BigDecimal("0.20"))
                .currency(Currency.getInstance("XAF"))
                .build();
    }

    /**
     * Validates that all required parameters are set.
     */
    public boolean validate() {
        return fuelPricePerLitre != null && fuelConsumptionL100km != null
                && vehicleWearCostPerKm != null && driverTimeValueXAFPerMin != null
                && penibilityBaseCostXAF != null && rainSurchargeCostXAF != null;
    }
}
