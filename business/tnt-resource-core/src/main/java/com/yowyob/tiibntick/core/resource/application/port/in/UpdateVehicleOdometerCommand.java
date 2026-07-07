package com.yowyob.tiibntick.core.resource.application.port.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

/**
 * Command to update the odometer reading of a vehicle.
 * @author MANFOUO Braun.
 */
public record UpdateVehicleOdometerCommand(
        @NotNull UUID tenantId,
        @NotNull UUID vehicleId,
        @PositiveOrZero double newOdometerKm
) {}
