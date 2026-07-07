package com.yowyob.tiibntick.core.resource.application.port.in;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to assign a vehicle to a deliverer for a mission.
 * @author MANFOUO Braun.
 */
public record AssignVehicleCommand(
        @NotNull UUID tenantId,
        @NotNull UUID vehicleId,
        @NotNull UUID delivererId,
        UUID missionId
) {}
