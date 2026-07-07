package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Command for a delivery person to respond to a client announcement.
 *
 * @author MANFOUO Braun
 */
public record RespondToAnnouncementCommand(
        @NotNull UUID tenantId,
        @NotNull UUID announcementId,
        @NotNull UUID deliveryPersonId,
        @NotNull Instant estimatedArrivalTime,
        String note
) {}
