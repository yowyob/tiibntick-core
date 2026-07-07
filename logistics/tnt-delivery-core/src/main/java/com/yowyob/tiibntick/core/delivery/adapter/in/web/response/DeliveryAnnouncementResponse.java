package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response DTO for {@code DeliveryAnnouncement}.
 *
 * @author MANFOUO Braun
 */
public record DeliveryAnnouncementResponse(
        UUID id,
        UUID tenantId,
        UUID clientId,
        String title,
        String description,
        BigDecimal offeredAmount,
        String currency,
        AnnouncementStatus status,
        DeliveryUrgency urgency,
        String pickupDisplay,
        String deliveryDisplay,
        String recipientName,
        int responseCount,
        List<ResponseSummary> responses,
        UUID selectedResponseId,
        UUID createdDeliveryId,
        Instant createdAt,
        Instant updatedAt
) {
    public record ResponseSummary(
            UUID id,
            UUID deliveryPersonId,
            Instant estimatedArrivalTime,
            String note,
            ResponseStatus status,
            Instant createdAt
    ) {}
}
