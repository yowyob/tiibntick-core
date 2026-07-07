package com.yowyob.tiibntick.core.resource.application.port.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Command to find the best available vehicle for a mission based on capacity requirements.
 * The matching algorithm selects the smallest vehicle that satisfies constraints to
 * minimise fuel consumption and operational cost.
 * @author MANFOUO Braun.
 */
public record FindBestVehicleCommand(
        @NotNull UUID tenantId,
        @NotNull UUID agencyId,
        @Positive double requiredWeightKg,
        @Positive double requiredVolumeM3,
        List<UUID> excludeVehicleIds
) {}
