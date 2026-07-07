package com.yowyob.tiibntick.core.resource.application.port.in;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to assign a piece of equipment to a field agent.
 * @author MANFOUO Braun.
 */
public record AssignEquipmentCommand(
        @NotNull UUID tenantId,
        @NotNull UUID equipmentId,
        @NotNull UUID userId
) {}
