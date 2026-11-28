package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Row D6: the parcel leaves a relay hub towards another relay. */
public record ResumeFromRelayHubCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId
) {}
