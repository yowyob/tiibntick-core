package com.yowyob.tiibntick.core.billing.cost.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;
import java.util.UUID;

/**
 * Command to compute the equipment-specific additional cost for a FreelancerOrg mission.
 *
 * <p>This is a lightweight command used for equipment cost preview or incremental
 * cost addition when special equipment (REFRIGERATED_BOX, CARGO_BAG, etc.) is deployed.
 *
 * @author MANFOUO Braun
 */
public record ComputeEquipmentCostCommand(
        /** Tenant identifier. */
        @NotNull UUID tenantId,

        /** Set of equipment type names (from EquipmentCostType) deployed. */
        @NotNull Set<String> equipmentTypes,

        /** Delivery distance in km (for variable cost calculation). */
        @Positive double distanceKm,

        /** Optional mission ID for tracing. */
        String missionId
) {}
