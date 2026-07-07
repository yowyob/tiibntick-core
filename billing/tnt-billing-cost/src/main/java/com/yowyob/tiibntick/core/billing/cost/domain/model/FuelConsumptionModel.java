package com.yowyob.tiibntick.core.billing.cost.domain.model;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.VehicleType;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FuelConsumptionModel — computes the fuel cost component of the operational cost.
 *
 * Formula (tiibntick(3).tex, Section 3.3):
 *   c_fuel(a) = d(a) × κ_carb(vehicle, load) × p_essence
 *
 * Where:
 *   d(a)               = arc distance in km
 *   κ_carb(vehicle, load) = effective consumption (L/100km) adjusted for load
 *   p_essence          = fuel price per litre (XAF/L from CostParameters)
 *
 * @author MANFOUO Braun
 */
@Builder
public record FuelConsumptionModel(
        VehicleType vehicleType,
        /** Urban driving penalty factor (stop-and-go traffic). Default = 1.25 */
        double urbanFactor,
        /** Off-road penalty factor for dirt/bush roads. Default = 1.60 */
        double offRoadFactor,
        /** Load sensitivity: extra fuel per unit load fraction. Default = 0.20 */
        double loadFactor
) {
    public FuelConsumptionModel {
        if (vehicleType == null) vehicleType = VehicleType.MOTORCYCLE;
        if (urbanFactor <= 0) urbanFactor = 1.25;
        if (offRoadFactor <= 0) offRoadFactor = 1.60;
        if (loadFactor < 0) loadFactor = 0.20;
    }

    public static FuelConsumptionModel forVehicle(VehicleType vehicleType) {
        return FuelConsumptionModel.builder()
                .vehicleType(vehicleType)
                .urbanFactor(1.25)
                .offRoadFactor(1.60)
                .loadFactor(0.20)
                .build();
    }

    /**
     * Computes effective fuel consumption in L/100km, adjusted for:
     *   - road type (urban / off-road multiplier)
     *   - current load fraction
     *   - parameter overrides from CostParameters
     *
     * @param context    the cost computation context
     * @param parameters tenant-specific parameters (may override defaults)
     * @return effective consumption in L/100km
     */
    public double effectiveConsumption(CostContext context, CostParameters parameters) {
        // : Per-vehicle consumption from tnt-resource-core overrides type average
        double base;
        if (context.vehicleFuelConsumptionL100km() != null) {
            base = context.vehicleFuelConsumptionL100km();
        } else if (parameters.fuelConsumptionL100km() != null) {
            base = parameters.fuelConsumptionL100km().doubleValue();
        } else {
            base = vehicleType.baseConsumptionL100km();
        }

        // Road type adjustment
        double roadMultiplier = switch (context.roadType()) {
            case HIGHWAY -> 0.90;
            case URBAN_PAVED -> urbanFactor;
            case DEGRADED -> urbanFactor * 1.30;
            case DIRT -> offRoadFactor;
            case OFF_ROAD -> offRoadFactor * 1.25;
            default -> urbanFactor;
        };

        // Load adjustment: more cargo = more fuel
        double loadSensitivity = parameters.loadSensitivity() != null
                ? parameters.loadSensitivity().doubleValue()
                : this.loadFactor;
        double loadMultiplier = 1.0 + loadSensitivity * context.loadFactor();

        return base * roadMultiplier * loadMultiplier;
    }

    /**
     * Computes total fuel cost for the arc.
     *
     * @param distanceKm arc distance
     * @param context    cost context
     * @param parameters tenant parameters
     * @return fuel cost as Money (in tenant currency)
     */
    public Money totalFuelCost(double distanceKm, CostContext context, CostParameters parameters) {
        if (vehicleType == VehicleType.BICYCLE) {
            return Money.zero(parameters.currency().getCurrencyCode());
        }
        double consumptionL100km = effectiveConsumption(context, parameters);
        // cost = dist × (consumptionL/100km / 100) × pricePerLitre
        BigDecimal costPerKm = parameters.fuelPricePerLitre()
                .multiply(BigDecimal.valueOf(consumptionL100km / 100.0))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalCost = costPerKm.multiply(BigDecimal.valueOf(distanceKm))
                .setScale(2, RoundingMode.HALF_UP);
        return new Money(totalCost, parameters.currency());
    }
}
