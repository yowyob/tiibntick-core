package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to confirm physical parcel pickup by the delivery person.
 *
 * @author MANFOUO Braun
 */
public record ConfirmPickupCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId
) {}
