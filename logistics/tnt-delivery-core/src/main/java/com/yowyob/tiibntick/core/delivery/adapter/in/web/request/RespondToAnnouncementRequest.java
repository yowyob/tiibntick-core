package com.yowyob.tiibntick.core.delivery.adapter.in.web.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * HTTP request body for a delivery person's response to an announcement.
 *
 * @author MANFOUO Braun
 */
public record RespondToAnnouncementRequest(
        @NotNull UUID deliveryPersonId,
        @NotNull Instant estimatedArrivalTime,
        String note,
        @DecimalMin("1") BigDecimal proposedPrice,
        String proposedCurrency
) {
    public RespondToAnnouncementRequest(
            UUID deliveryPersonId,
            Instant estimatedArrivalTime,
            String note) {
        this(deliveryPersonId, estimatedArrivalTime, note, null, null);
    }
}
