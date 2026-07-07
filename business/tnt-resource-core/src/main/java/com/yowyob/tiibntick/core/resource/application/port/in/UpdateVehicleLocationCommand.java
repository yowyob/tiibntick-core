package com.yowyob.tiibntick.core.resource.application.port.in;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to update the GPS location of a vehicle in real time.
 * @author MANFOUO Braun.
 */
public record UpdateVehicleLocationCommand(
        @NotNull UUID tenantId,
        @NotNull UUID vehicleId,
        double latitude,
        double longitude
) {}
