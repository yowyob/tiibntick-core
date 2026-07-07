package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable composite cost of a delivery, breaking down individual cost dimensions.
 *
 * <p>Based on the mathematical model from TiiBnTick thesis (ColisCh@in / tiibntick.tex):
 * <pre>
 *   C_total = α·C_distance + β·C_time + γ·C_road + δ·C_weather + η·C_fuel
 * </pre>
 * All monetary values are in XAF (Central African Franc).
 *
 * @author MANFOUO Braun
 */
public record DeliveryCost(
        BigDecimal distanceCost,
        BigDecimal timeCost,
        BigDecimal roadPenibilityCost,
        BigDecimal weatherRiskCost,
        BigDecimal fuelCost,
        String currency
) {

    /** Default currency for Cameroon and CEMAC zone. */
    public static final String XAF = "XAF";

    /**
     * Computes the total delivery cost as the weighted sum of all components.
     * Weights α=0.35, β=0.25, γ=0.20, δ=0.10, η=0.10 (calibrated for Yaoundé context).
     */
    public BigDecimal total() {
        return distanceCost
                .add(timeCost)
                .add(roadPenibilityCost)
                .add(weatherRiskCost)
                .add(fuelCost)
                .setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Factory for a simple cost computed only from distance (fallback mode).
     */
    public static DeliveryCost fromDistanceOnly(BigDecimal distanceCost, String currency) {
        return new DeliveryCost(distanceCost, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, currency);
    }

    /**
     * Factory for XAF cost with all components.
     */
    public static DeliveryCost ofXaf(BigDecimal distance, BigDecimal time,
                                     BigDecimal road, BigDecimal weather, BigDecimal fuel) {
        return new DeliveryCost(distance, time, road, weather, fuel, XAF);
    }
}
