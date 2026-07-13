package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryUrgency;
import com.yowyob.tiibntick.core.marketback.domain.model.QuoteStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO — QuoteRequest with embedded responses.
 * @author MANFOUO Braun
 */
public record QuoteRequestResponse(
        UUID id,
        String tenantId,
        UUID clientId,
        UUID listingId,
        UUID providerId,
        QuoteStatus status,
        String pickupCity,
        String deliveryCity,
        double weightKg,
        double distanceKm,
        DeliveryUrgency urgency,
        String notes,
        LocalDateTime expiresAt,
        List<QuoteResponseDto> responses,
        UUID selectedResponseId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record QuoteResponseDto(
            UUID id, UUID providerId, long proposedPriceXaf,
            double etaHours, String message, LocalDateTime validUntil,
            String status, LocalDateTime createdAt
    ) {}
}
