package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to resume transit after a relay point stop.
 *
 * @author MANFOUO Braun
 */
public record ResumeFromRelayPointCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId
) {}
