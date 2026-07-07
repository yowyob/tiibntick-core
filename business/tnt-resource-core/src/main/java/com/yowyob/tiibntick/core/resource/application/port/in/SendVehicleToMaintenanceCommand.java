package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.MaintenanceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to send a vehicle to maintenance.
 * @author MANFOUO Braun.
 */
public record SendVehicleToMaintenanceCommand(
        @NotNull UUID tenantId,
        @NotNull UUID vehicleId,
        @NotNull MaintenanceType maintenanceType,
        @NotBlank String reason,
        LocalDate scheduledDate,
        Double odometerThresholdKm,
        String technicianName
) {}
