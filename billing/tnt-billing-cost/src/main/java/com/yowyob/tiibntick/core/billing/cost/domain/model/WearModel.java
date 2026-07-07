package com.yowyob.tiibntick.core.billing.cost.domain.model;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.VehicleType;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * WearModel — computes the vehicle mechanical wear cost component.
 *
 * Formula (tiibntick(3).tex, Section 3.3):
 *   c_wear(a) = d(a) × κ_usure × ρ(a) × δ(weather)
 *
 * Where:
 *   d(a)      = arc distance in km
 *   κ_usure   = base wear cost per km (XAF/km from CostParameters)
 *   ρ(a)      = road degradation factor from RoadType enum
 *   δ(weather) = weather-induced wear factor from WeatherCondition enum
 *
 * @author MANFOUO Braun
 */
@Builder
public record WearModel(VehicleType vehicleType) {

    public static WearModel forVehicle(VehicleType vehicleType) {
        return new WearModel(vehicleType != null ? vehicleType : VehicleType.MOTORCYCLE);
    }

    /**
     * Computes the effective wear cost for a given arc.
     *
     * @param distanceKm arc distance in km
     * @param context    contains roadType and weatherCondition
     * @param parameters tenant-specific parameters (κ_usure from CostParameters)
     * @return wear cost as Money
     */
    public Money effectiveWearCost(double distanceKm, CostContext context, CostParameters parameters) {
        double baseWearPerKm = parameters.vehicleWearCostPerKm() != null
                ? parameters.vehicleWearCostPerKm().doubleValue()
                : vehicleType.baseWearPerKm() * 1000; // convert relative to absolute XAF

        double rho = context.roadType().degradationFactor();   // ρ(a)
        double delta = context.weatherCondition().wearFactor(); // δ(weather)

        double wearPerKm = baseWearPerKm * rho * delta;
        BigDecimal totalWear = BigDecimal.valueOf(wearPerKm * distanceKm)
                .setScale(2, RoundingMode.HALF_UP);
        return new Money(totalWear, parameters.currency());
    }
}
