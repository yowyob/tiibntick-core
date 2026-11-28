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
        @NotNull UUID responseId,
        /** Lot L5: id of the delivery to create, chosen when the escrow was held; {@code null} → random. */
        UUID deliveryId
) {
    public SelectAnnouncementResponseCommand(UUID tenantId, UUID announcementId, UUID clientId, UUID responseId) {
        this(tenantId, announcementId, clientId, responseId, null);
    }
}
