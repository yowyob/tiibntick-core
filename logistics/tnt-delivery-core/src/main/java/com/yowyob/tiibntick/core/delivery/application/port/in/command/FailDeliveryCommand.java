package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to mark a delivery as failed due to an incident.
 *
 * @author MANFOUO Braun
 */
public record FailDeliveryCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId,
        @NotBlank String reason
) {}
