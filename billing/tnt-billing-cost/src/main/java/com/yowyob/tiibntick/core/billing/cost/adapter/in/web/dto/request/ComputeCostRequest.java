package com.yowyob.tiibntick.core.billing.cost.adapter.in.web.dto.request;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.MissionPriority;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.RoadType;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.VehicleType;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.WeatherCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Set;
import java.util.UUID;

/**
 * REST request DTO for operational cost computation.
 *
 * <p> — Added FreelancerOrg fleet context:
 * <ul>
 *   <li>{@link #ownerOrgId} — UUID of the FreelancerOrg or Agency (for fleet params lookup).</li>
 *   <li>{@link #activeEquipmentTypes} — special equipment deployed (extra cost).</li>
 *   <li>{@link #vehicleFuelConsumptionL100km} — per-vehicle fuel consumption override.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record ComputeCostRequest(
        String missionId,
        @NotNull UUID tenantId,
        @Positive double distanceKm,
        @PositiveOrZero Integer estimatedDurationMin,
        RoadType roadType,
        WeatherCondition weatherCondition,
        VehicleType vehicleType,
        MissionPriority priority,
        @PositiveOrZero double payloadWeightKg,
        @PositiveOrZero double vehicleCapacityKg,

        // : FreelancerOrg fleet context
        @Schema(description = "UUID of the FreelancerOrg or Agency (for fleet params lookup)",
                example = "550e8400-e29b-41d4-a716-446655440000")
        String ownerOrgId,

        @Schema(description = "Equipment type names deployed in this mission",
                example = "[\"REFRIGERATED_BOX\", \"CARGO_BAG\"]")
        Set<String> activeEquipmentTypes,

        @Schema(description = "Per-vehicle fuel consumption in L/100km from tnt-resource-core",
                example = "4.5")
        Double vehicleFuelConsumptionL100km
) {}
