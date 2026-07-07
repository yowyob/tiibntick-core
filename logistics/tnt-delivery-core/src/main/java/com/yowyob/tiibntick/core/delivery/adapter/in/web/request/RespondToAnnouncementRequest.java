package com.yowyob.tiibntick.core.delivery.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;
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
        String note
) {}
