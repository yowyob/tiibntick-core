package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to deposit a parcel at a relay / hub point.
 *
 * @author MANFOUO Braun
 */
public record DepositAtRelayPointCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId,
        @NotNull UUID relayPointId
) {}
