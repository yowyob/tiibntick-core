package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BoardEntryResponse(
        UUID id,
        UUID clientId,
        String title,
        String description,
        BigDecimal offeredAmount,
        String currency,
        Double weightKg,
        String pickupAddress,
        Double pickupLatitude,
        Double pickupLongitude,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String recipientName,
        String urgency,
        String status,
        List<BoardResponseSummary> responses,
        UUID selectedResponseId,
        UUID createdDeliveryId,
        Instant createdAt,
        Instant updatedAt
) {
}
