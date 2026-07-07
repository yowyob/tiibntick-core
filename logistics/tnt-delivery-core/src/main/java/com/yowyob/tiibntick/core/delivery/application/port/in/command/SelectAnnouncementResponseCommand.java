package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command for a client to select a delivery person's response, triggering delivery creation.
 *
 * @author MANFOUO Braun
 */
public record SelectAnnouncementResponseCommand(
        @NotNull UUID tenantId,
        @NotNull UUID announcementId,
        @NotNull UUID clientId,
        @NotNull UUID responseId
) {}
