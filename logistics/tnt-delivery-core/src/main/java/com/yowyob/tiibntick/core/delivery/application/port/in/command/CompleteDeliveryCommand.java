package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to mark a delivery as successfully completed.
 *
 * @author MANFOUO Braun
 */
public record CompleteDeliveryCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId,
        String proofPhotoUrl
) {}
