package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to register a new piece of equipment in the TiiBnTick fleet.
 * @author MANFOUO Braun.
 */
public record CreateEquipmentCommand(
        @NotNull UUID tenantId,
        @NotNull UUID organizationId,
        @NotNull UUID branchId,
        @NotNull EquipmentType type,
        @NotBlank String serialNumber,
        String description,
        LocalDate purchasedAt,
        LocalDate warrantyExpiresAt
) {}
