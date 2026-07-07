package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to cancel a delivery (client-initiated, before pickup).
 *
 * @author MANFOUO Braun
 */
public record CancelDeliveryCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID requesterId,
        @NotBlank String reason
) {}
