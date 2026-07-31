package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Command for a delivery person to respond to a client announcement.
 *
 * <p>{@code proposedPrice} is required when the announcement is {@code QUOTE_REQUEST}
 * (validated in the aggregate).
 *
 * @author MANFOUO Braun
 */
public record RespondToAnnouncementCommand(
        @NotNull UUID tenantId,
        @NotNull UUID announcementId,
        @NotNull UUID deliveryPersonId,
        @NotNull Instant estimatedArrivalTime,
        String note,
        @DecimalMin("1") BigDecimal proposedPrice,
        String proposedCurrency
) {
    /**
     * Backward-compatible constructor without proposed price.
     */
    public RespondToAnnouncementCommand(
            UUID tenantId,
            UUID announcementId,
            UUID deliveryPersonId,
            Instant estimatedArrivalTime,
            String note) {
        this(tenantId, announcementId, deliveryPersonId, estimatedArrivalTime, note, null, null);
    }
}
