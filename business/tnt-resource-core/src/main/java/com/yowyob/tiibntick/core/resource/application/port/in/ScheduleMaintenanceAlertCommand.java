package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.MaintenanceType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to explicitly schedule a maintenance alert for a vehicle.
 * @author MANFOUO Braun.
 */
public record ScheduleMaintenanceAlertCommand(
        @NotNull UUID tenantId,
        @NotNull UUID vehicleId,
        @NotNull MaintenanceType type,
        @NotNull String reason,
        @NotNull LocalDate scheduledDate,
        Double odometerThresholdKm
) {}
