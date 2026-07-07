package com.yowyob.tiibntick.core.billing.cost.domain.model;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.MissionPriority;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.RoadType;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.VehicleType;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.WeatherCondition;
import lombok.Builder;

import java.util.Set;
import java.util.UUID;

/**
 * CostContext — value object encapsulating all inputs required for an operational cost computation.
 *
 * <p>Matches the mathematical model's context variables:
 * dist (km), duration (min), ρ (road type), δ (weather), vehicle type, tenant params.
 *
 * <p> — Added FreelancerOrg fleet context:
 * <ul>
 *   <li>{@link #fleetCostParameters} — per-actor fleet cost parameters, overriding global params.</li>
 *   <li>{@link #activeEquipmentTypes} — equipment types deployed in this mission.</li>
 *   <li>{@link #ownerOrgId} — the FreelancerOrg or Agency UUID (for fleet params lookup).</li>
 * </ul>
 *
 * <p>Reference: tiibntick(3).tex — Section 3.3 "Coût Carburant et Usure"
 *
 * @author MANFOUO Braun
 */
@Builder
public record CostContext(
        /** Unique identifier of the mission being costed. */
        String missionId,
        /** Tenant identifier — used to load tenant-specific parameters. */
        UUID tenantId,
        /** Route distance in kilometres. */
        double distanceKm,
        /** Estimated travel duration in minutes (including stops). */
        int estimatedDurationMin,
        /** Dominant road type on the route. */
        RoadType roadType,
        /** Current or forecast weather condition at delivery time. */
        WeatherCondition weatherCondition,
        /** Type of delivery vehicle assigned to the mission. */
        VehicleType vehicleType,
        /** Mission priority affecting time-value multiplier. */
        MissionPriority priority,
        /**
         * Payload weight in kg (affects fuel consumption via load factor).
         * Used in the extended fuel model: κ_carb × (1 + loadFactor × weight/capacity).
         */
        double payloadWeightKg,
        /**
         * Vehicle maximum load capacity in kg.
         * Defaults to vehicle-type-specific default if not set.
         */
        double vehicleCapacityKg,

        // ─── : FreelancerOrg fleet context ──────────────────────────────

        /**
         * Fleet-specific cost parameters for this actor's fleet.
         * If non-null, these override the global {@link CostParameters} for:
         *   - fuel price per liter
         *   - vehicle wear rate per km
         *   - time value per hour
         *   - terrain degradation factor
         *   - rain penalty factor
         * If null, global CostParameters from tnt-settings-core are used.
         */
        FleetCostParameters fleetCostParameters,

        /**
         * Set of equipment type names (from EquipmentCostType) actively deployed
         * in this mission. Used to compute equipment-specific additional costs.
         * Null or empty = no special equipment.
         */
        Set<String> activeEquipmentTypes,

        /**
         * UUID of the owning FreelancerOrg or Agency.
         * Used for fleet parameter lookup when {@link #fleetCostParameters} is null.
         * Also used as the tenant partition key in fleet_cost_parameters table.
         */
        String ownerOrgId,

        /**
         * Vehicle-specific fuel consumption in L/100km from tnt-resource-core.
         * When non-null, this overrides the vehicle type's default consumption.
         * Enables per-vehicle accuracy instead of per-type average.
         */
        Double vehicleFuelConsumptionL100km
) {
    public CostContext {
        if (distanceKm < 0) throw new IllegalArgumentException("distanceKm must be >= 0");
        if (estimatedDurationMin < 0) throw new IllegalArgumentException("estimatedDurationMin must be >= 0");
        if (roadType == null) roadType = RoadType.URBAN_PAVED;
        if (weatherCondition == null) weatherCondition = WeatherCondition.CLEAR;
        if (vehicleType == null) vehicleType = VehicleType.MOTORCYCLE;
        if (priority == null) priority = MissionPriority.NORMAL;
        if (payloadWeightKg < 0) throw new IllegalArgumentException("payloadWeightKg must be >= 0");
        if (vehicleCapacityKg <= 0) vehicleCapacityKg = vehicleType.defaultCapacityKg();
    }

    /**
     * Load factor in [0,1]: ratio of payload to vehicle capacity.
     * Used to scale fuel consumption for loaded vehicles.
     */
    public double loadFactor() {
        return Math.min(payloadWeightKg / vehicleCapacityKg, 1.0);
    }

    /**
     * Returns true if fleet-specific cost parameters are available for this context.
     */
    public boolean hasFleetParameters() {
        return fleetCostParameters != null;
    }

    /**
     * Returns true if any special equipment is deployed in this mission.
     */
    public boolean hasEquipment() {
        return activeEquipmentTypes != null && !activeEquipmentTypes.isEmpty();
    }

    /**
     * Returns the effective terrain degradation factor.
     * Uses fleet parameters if available, otherwise falls back to road type default.
     */
    public double effectiveTerrainFactor() {
        if (hasFleetParameters() && fleetCostParameters.terrainDegradationFactor() != null) {
            return fleetCostParameters.effectiveTerrainFactor() * roadType.degradationFactor();
        }
        return roadType.degradationFactor();
    }

    /**
     * Returns the effective rain penalty factor for weather-adjusted cost computation.
     * Uses fleet parameters if available, otherwise uses weather condition factor.
     */
    public double effectiveWeatherFactor() {
        double baseFactor = weatherCondition.wearFactor();
        if (hasFleetParameters() && fleetCostParameters.rainPenaltyFactor() != null) {
            // Only apply fleet rain penalty for rain conditions
            boolean isRain = weatherCondition != WeatherCondition.CLEAR
                    && weatherCondition != WeatherCondition.UNKNOWN;
            if (isRain) {
                return baseFactor * fleetCostParameters.effectiveRainPenaltyFactor();
            }
        }
        return baseFactor;
    }
}
