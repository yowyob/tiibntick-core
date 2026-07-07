package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;
import lombok.With;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Operational cost parameters for a FreelancerOrganization's fleet.
 *
 * <p>These parameters are used by the billing engine to compute the true operational
 * cost of a delivery (analogous to the formula in the mathematical model):
 * <pre>
 *   cost = distKm × (fuelPriceLiterXAF × fuelConsumptionL100km / 100)
 *        + distKm × vehicleWearRatePerKm × terrainDegradationFactor
 *        + durationMin × timeValuePerHour / 60
 *        + (isRaining ? rainPenaltyFactor × basePrice : 0)
 * </pre>
 *
 * <p>Parameters can be auto-updated from a fuel price feed when
 * {@link #autoUpdateFuelPrice} is {@code true}.
 *
 * @author MANFOUO Braun
 */
@Value
@Jacksonized
@Builder(toBuilder = true)
public class FleetCostParameters {

    /**
     * UUID of the FreelancerOrganization that owns these parameters.
     * Logical reference — no physical FK to tnt-organization-core.
     */
    String ownerOrgId;

    /**
     * Current pump price of fuel in XAF per litre.
     * Typical Cameroon value: ~700–750 XAF/L.
     * Updated periodically from tnt-settings-core when autoUpdateFuelPrice=true.
     */
    @With
    BigDecimal fuelPriceLiterXAF;

    /**
     * Vehicle wear cost in XAF per kilometre (maintenance, tyres, depreciation).
     * Typical value: 15–40 XAF/km depending on vehicle type.
     */
    BigDecimal vehicleWearRatePerKm;

    /**
     * Monetary value of the deliverer's time in XAF per hour.
     * Used to compute time-opportunity cost for long routes.
     * Typical value: 500–1500 XAF/h.
     */
    BigDecimal timeValuePerHour;

    /**
     * Multiplicative factor for terrain degradation (unpaved roads, mud).
     * Applied to vehicle wear rate. Values: 1.0 (paved) to 2.0 (mud).
     */
    BigDecimal terrainDegradationFactor;

    /**
     * Penalty factor applied to the base price when it is raining.
     * Represents additional risk and slow-down cost.
     * Values: 0.05 to 0.20 (5% to 20% penalty on base price).
     */
    BigDecimal rainPenaltyFactor;

    /**
     * When {@code true}, the fuel price is auto-updated by a background job
     * that fetches current pump prices from tnt-settings-core or an external feed.
     * When {@code false}, the {@link #fuelPriceLiterXAF} value is locked.
     */
    boolean autoUpdateFuelPrice;

    /** Timestamp of the last fuel price update. */
    @With
    Instant lastFuelPriceUpdate;

    /**
     * Computes the estimated operational cost for a delivery.
     *
     * @param distKm        distance in kilometres
     * @param durationMin   estimated duration in minutes
     * @param terrainFactor additional terrain factor from route (1.0 for paved, up to 2.0)
     * @param rainFactor    1.0 if no rain, 0 if rain penalty applies
     * @param basePrice     base selling price (for rain penalty computation)
     * @return estimated operational cost in XAF
     */
    public BigDecimal estimateCost(double distKm, double durationMin,
                                    double terrainFactor, boolean isRaining,
                                    BigDecimal basePrice) {
        BigDecimal dist = BigDecimal.valueOf(distKm);
        BigDecimal dur  = BigDecimal.valueOf(durationMin);

        // Fuel cost
        BigDecimal fuelCost = fuelPriceLiterXAF
                .multiply(BigDecimal.valueOf(6.0 / 100.0)) // ~6L/100km average consumption
                .multiply(dist);

        // Wear cost (with terrain factor)
        BigDecimal effectiveTerrain = (terrainDegradationFactor != null)
                ? terrainDegradationFactor.multiply(BigDecimal.valueOf(terrainFactor))
                : BigDecimal.valueOf(terrainFactor);
        BigDecimal wearCost = vehicleWearRatePerKm.multiply(dist).multiply(effectiveTerrain);

        // Time cost
        BigDecimal timeCost = timeValuePerHour.multiply(dur).divide(BigDecimal.valueOf(60), 2,
                java.math.RoundingMode.HALF_UP);

        // Rain penalty
        BigDecimal rainPenalty = BigDecimal.ZERO;
        if (isRaining && rainPenaltyFactor != null && basePrice != null) {
            rainPenalty = basePrice.multiply(rainPenaltyFactor);
        }

        return fuelCost.add(wearCost).add(timeCost).add(rainPenalty)
                .setScale(0, java.math.RoundingMode.HALF_UP);
    }
}
