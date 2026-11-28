package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Row D8: last leg towards the recipient (from the destination relay, or a new attempt). */
public record StartFinalLegCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId
) {}
