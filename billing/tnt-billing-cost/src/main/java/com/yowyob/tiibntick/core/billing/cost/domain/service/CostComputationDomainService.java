package com.yowyob.tiibntick.core.billing.cost.domain.service;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.WeatherCondition;
import com.yowyob.tiibntick.core.billing.cost.domain.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CostComputationDomainService — stateless domain service implementing
 * the full five-component operational cost formula.
 *
 * <p>Formula (tiibntick(3).tex — Chapter 3):
 * <pre>
 *   c_total = c_fuel + c_wear + c_time + c_penibility + c_weather + c_equipment
 * </pre>
 *
 * <p> — Added FreelancerOrg fleet parameter integration:
 * <ul>
 *   <li>Fleet's terrain degradation factor applied to wear cost.</li>
 *   <li>Fleet's rain penalty factor amplifies weather surcharge.</li>
 *   <li>Vehicle-specific fuel consumption from FreelancerVehicle overrides type default.</li>
 *   <li>{@link #computeEquipmentCost(CostContext)} computes equipment additional cost.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public final class CostComputationDomainService {

    private CostComputationDomainService() {}

    /**
     * Computes the full operational cost for a delivery mission arc.
     *
     * @param context    all mission-specific inputs
     * @param parameters tenant-specific calibrated parameters (used as base/fallback)
     * @return immutable OperationalCost breakdown
     */
    public static OperationalCost compute(CostContext context, CostParameters parameters) {
        // When FleetCostParameters are present, merge them into effective parameters
        CostParameters effective = mergeWithFleetParams(context, parameters);
        String currency = effective.currency().getCurrencyCode();

        // ── c_fuel: d × (κ_carb/100) × p_essence × load_adj × road_adj ──────
        FuelConsumptionModel fuelModel = FuelConsumptionModel.forVehicle(context.vehicleType());
        Money fuelCost = fuelModel.totalFuelCost(context.distanceKm(), context, effective);

        // ── c_wear: d × κ_usure × ρ(road) × δ(weather) with fleet factor ─────
        WearModel wearModel = WearModel.forVehicle(context.vehicleType());
        Money wearCost = wearModel.effectiveWearCost(context.distanceKm(), context, effective);

        // ── c_time: duration_min × v_temps × priority_multiplier ─────────────
        Money timeCost = computeTimeCost(context, effective, currency);

        // ── c_penibility: ρ_index × penalty_base ─────────────────────────────
        Money penibilityCost = computePenibilityCost(context, effective, currency);

        // ── c_weather: p_rain × φ_rain × fleet_rain_factor ────────────────────
        Money weatherSurcharge = computeWeatherSurcharge(context, effective, currency);

        // ── c_equipment: special equipment additional cost () ─────────────
        Money equipmentCost = computeEquipmentCost(context, currency);

        return OperationalCost.builder()
                .fuelCost(fuelCost)
                .vehicleWearCost(wearCost)
                .timeCost(timeCost)
                .penibilityCost(penibilityCost)
                .weatherSurcharge(weatherSurcharge)
                .otherCosts(equipmentCost)
                .currencyCode(currency)
                .build();
    }

    // ── private formula implementations ────────────────────────────────────────

    /**
     * Merges global CostParameters with fleet-specific overrides.
     * Fleet parameters (if present) override: fuel price, wear rate, time value.
     */
    private static CostParameters mergeWithFleetParams(CostContext context, CostParameters base) {
        if (!context.hasFleetParameters()) return base;
        FleetCostParameters fleet = context.fleetCostParameters();

        return CostParameters.builder()
                .fuelPricePerLitre(
                        fleet.fuelPriceLiterXAF() != null ? fleet.fuelPriceLiterXAF()
                                : base.fuelPricePerLitre())
                .fuelConsumptionL100km(
                        context.vehicleFuelConsumptionL100km() != null
                                ? BigDecimal.valueOf(context.vehicleFuelConsumptionL100km())
                                : base.fuelConsumptionL100km())
                .vehicleWearCostPerKm(
                        fleet.vehicleWearRatePerKm() != null ? fleet.vehicleWearRatePerKm()
                                : base.vehicleWearCostPerKm())
                .driverTimeValueXAFPerMin(
                        fleet.timeValuePerMinute() != null ? fleet.timeValuePerMinute()
                                : base.driverTimeValueXAFPerMin())
                .penibilityBaseCostXAF(base.penibilityBaseCostXAF())
                .rainSurchargeCostXAF(base.rainSurchargeCostXAF())
                .floodSurchargeCostXAF(base.floodSurchargeCostXAF())
                .loadSensitivity(base.loadSensitivity())
                .currency(base.currency())
                .build();
    }

    /**
     * c_time = duration_min × v_temps × priority_multiplier
     */
    private static Money computeTimeCost(CostContext context, CostParameters parameters,
                                          String currency) {
        double timeValue = parameters.driverTimeValueXAFPerMin() != null
                ? parameters.driverTimeValueXAFPerMin().doubleValue() : 15.0;
        double rawCost = context.estimatedDurationMin() * timeValue * context.priority().timeValueMultiplier();
        return new Money(BigDecimal.valueOf(rawCost).setScale(2, RoundingMode.HALF_UP),
                parameters.currency());
    }

    /**
     * c_penibility = ρ_index(road) × penalty_base × terrain_factor(fleet)
     *
     * <p>: The fleet's terrain degradation factor amplifies the penibility cost
     * to reflect the actor's knowledge of the actual road conditions in their operating area.
     */
    private static Money computePenibilityCost(CostContext context, CostParameters parameters,
                                                 String currency) {
        double penibilityIndex = context.roadType().penibilityIndex();
        double penaltyBase = parameters.penibilityBaseCostXAF() != null
                ? parameters.penibilityBaseCostXAF().doubleValue() : 200.0;

        // : Apply fleet terrain factor if available
        double terrainFactor = context.hasFleetParameters()
                ? context.fleetCostParameters().effectiveTerrainFactor() : 1.0;
        double rawCost = penibilityIndex * penaltyBase * terrainFactor;

        return new Money(BigDecimal.valueOf(rawCost).setScale(2, RoundingMode.HALF_UP),
                parameters.currency());
    }

    /**
     * c_weather = p_rain × φ_rain × fleet_rain_penalty_factor
     *
     * <p>: The fleet's rain penalty factor amplifies the weather surcharge
     * to reflect the actor's real experience with weather impact on their fleet.
     */
    private static Money computeWeatherSurcharge(CostContext context, CostParameters parameters,
                                                   String currency) {
        WeatherCondition weather = context.weatherCondition();

        if (weather == WeatherCondition.CLEAR || weather == WeatherCondition.UNKNOWN) {
            return Money.zero(currency);
        }

        double fleetRainFactor = context.hasFleetParameters()
                ? context.fleetCostParameters().effectiveRainPenaltyFactor() : 1.0;

        if (weather == WeatherCondition.FLOOD) {
            double floodSurcharge = parameters.floodSurchargeCostXAF() != null
                    ? parameters.floodSurchargeCostXAF().doubleValue() : 1000.0;
            return new Money(BigDecimal.valueOf(floodSurcharge * fleetRainFactor)
                    .setScale(2, RoundingMode.HALF_UP), parameters.currency());
        }

        double pRain = weather.rainProbability();
        double phiRain = parameters.rainSurchargeCostXAF() != null
                ? parameters.rainSurchargeCostXAF().doubleValue() : 300.0;
        if (weather == WeatherCondition.HEAVY_RAIN) phiRain *= 1.5;

        double rawCost = pRain * phiRain * fleetRainFactor;
        return new Money(BigDecimal.valueOf(rawCost).setScale(2, RoundingMode.HALF_UP),
                parameters.currency());
    }

    /**
     * c_equipment = sum of equipment-specific fixed + variable costs ().
     *
     * <p>Adds the operational cost of special equipment (refrigerated box, tracker, etc.)
     * to the mission cost. This cost is placed in the {@code otherCosts} component.
     */
    private static Money computeEquipmentCost(CostContext context, String currency) {
        if (!context.hasEquipment()) {
            return Money.zero(currency);
        }
        EquipmentCostResult result = EquipmentCostResult.compute(
                context.activeEquipmentTypes(), context.distanceKm());
        return new Money(result.totalCostXaf(), java.util.Currency.getInstance("XAF"));
    }

    /**
     * Computes the composite arc cost ω(a) used in route optimization.
     */
    public static double computeNormalizedArcCost(CostContext context, CostParameters parameters,
                                                    double[] weights, double maxCost) {
        if (maxCost <= 0) return 0.0;
        OperationalCost cost = compute(context, parameters);
        double totalCost = cost.total().amount().doubleValue();
        return Math.min(totalCost / maxCost, 1.0);
    }
}
