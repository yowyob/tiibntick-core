package com.yowyob.tiibntick.core.billing.cost.domain.model;

import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OperationalCost — immutable value object representing the complete
 * operational cost breakdown for a single delivery mission.
 *
 * Aggregates all five cost components from the mathematical model:
 *   c_total = c_fuel + c_wear + c_time + c_penibility + c_weather
 *
 * Reference: tiibntick(3).tex — Section 3 "Modélisation Mathématique"
 *
 * @author MANFOUO Braun
 */
@Builder
public record OperationalCost(
        /**
         * c_fuel — fuel consumption cost.
         * Formula: dist × (κ_carb/100) × p_essence
         */
        Money fuelCost,
        /**
         * c_wear — vehicle mechanical wear and maintenance cost.
         * Formula: dist × κ_usure × ρ(road) × δ(weather)
         */
        Money vehicleWearCost,
        /**
         * c_time — driver time opportunity cost.
         * Formula: duration_min × v_temps × priority_multiplier
         */
        Money timeCost,
        /**
         * c_penibility — penibility cost for difficult road conditions.
         * Formula: ρ_index(road) × penalty_base
         */
        Money penibilityCost,
        /**
         * c_weather — weather surcharge.
         * Formula: p_rain × φ_rain (probabilistic, from WeatherCondition)
         */
        Money weatherSurcharge,
        /** Other miscellaneous costs (tolls, parking, etc.). */
        Money otherCosts,
        /** The currency used for all amounts. */
        String currencyCode
) {
    /**
     * Computes the total operational cost.
     *
     * @return sum of all five cost components
     */
    public Money total() {
        return fuelCost
                .add(vehicleWearCost)
                .add(timeCost)
                .add(penibilityCost)
                .add(weatherSurcharge)
                .add(otherCosts);
    }

    /**
     * Returns a labelled breakdown map for reporting and display purposes.
     *
     * @return ordered map of cost component name → Money amount
     */
    public Map<String, Money> breakdown() {
        Map<String, Money> map = new LinkedHashMap<>();
        map.put("fuel", fuelCost);
        map.put("vehicleWear", vehicleWearCost);
        map.put("time", timeCost);
        map.put("penibility", penibilityCost);
        map.put("weatherSurcharge", weatherSurcharge);
        map.put("other", otherCosts);
        map.put("total", total());
        return map;
    }

    /**
     * Returns the cost normalized as a percentage contribution of each component to the total.
     *
     * @return map of component name → percentage (double in [0, 100])
     */
    public Map<String, Double> breakdownPercentages() {
        Money totalAmount = total();
        if (totalAmount.isZero()) return Map.of();

        Map<String, Double> percentages = new LinkedHashMap<>();
        breakdown().forEach((name, amount) -> {
            if (!"total".equals(name)) {
                double pct = amount.amount().doubleValue()
                        / totalAmount.amount().doubleValue() * 100.0;
                percentages.put(name, Math.round(pct * 100.0) / 100.0);
            }
        });
        return percentages;
    }

    public static OperationalCost zero(String currencyCode) {
        Money zero = Money.zero(currencyCode);
        return OperationalCost.builder()
                .fuelCost(zero)
                .vehicleWearCost(zero)
                .timeCost(zero)
                .penibilityCost(zero)
                .weatherSurcharge(zero)
                .otherCosts(zero)
                .currencyCode(currencyCode)
                .build();
    }
}
