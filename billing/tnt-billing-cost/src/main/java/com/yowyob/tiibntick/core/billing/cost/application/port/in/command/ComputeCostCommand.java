package com.yowyob.tiibntick.core.billing.cost.application.port.in.command;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.MissionPriority;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.RoadType;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.VehicleType;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.WeatherCondition;
import com.yowyob.tiibntick.core.billing.cost.domain.model.FleetCostParameters;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Set;
import java.util.UUID;

/**
 * Command to request an operational cost computation for a mission arc.
 *
 * <p> — Added FreelancerOrg fleet context fields:
 * <ul>
 *   <li>{@link #fleetCostParameters} — optional per-fleet cost parameters.</li>
 *   <li>{@link #activeEquipmentTypes} — deployed equipment types (extra cost).</li>
 *   <li>{@link #ownerOrgId} — the org UUID for fleet param lookup.</li>
 *   <li>{@link #vehicleFuelConsumptionL100km} — per-vehicle fuel consumption.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record ComputeCostCommand(
        String missionId,
        @NotNull UUID tenantId,
        @Positive double distanceKm,
        @PositiveOrZero int estimatedDurationMin,
        RoadType roadType,
        WeatherCondition weatherCondition,
        VehicleType vehicleType,
        MissionPriority priority,
        @PositiveOrZero double payloadWeightKg,
        @PositiveOrZero double vehicleCapacityKg,

        // ─── : FreelancerOrg fleet context ───────────────────────────────

        /**
         * Optional pre-loaded fleet cost parameters for this actor.
         * If provided, overrides global CostParameters for this computation.
         * Null = use global parameters from tnt-settings-core.
         */
        FleetCostParameters fleetCostParameters,

        /**
         * Set of equipment type names (from EquipmentCostType) deployed in this mission.
         * Null or empty = no special equipment costs.
         */
        Set<String> activeEquipmentTypes,

        /**
         * UUID of the owning FreelancerOrg or Agency.
         * Used for fleet parameter lookup when fleetCostParameters is null.
         */
        String ownerOrgId,

        /**
         * Per-vehicle fuel consumption in L/100km from tnt-resource-core.
         * When non-null, overrides vehicle type's default consumption.
         */
        Double vehicleFuelConsumptionL100km
) {
    /**
     * Backward-compatible constructor without new  fields.
     */
    public static ComputeCostCommand basic(String missionId, UUID tenantId, double distanceKm,
            int estimatedDurationMin, RoadType roadType, WeatherCondition weatherCondition,
            VehicleType vehicleType, MissionPriority priority,
            double payloadWeightKg, double vehicleCapacityKg) {
        return new ComputeCostCommand(missionId, tenantId, distanceKm, estimatedDurationMin,
                roadType, weatherCondition, vehicleType, priority, payloadWeightKg,
                vehicleCapacityKg, null, null, null, null);
    }
}
