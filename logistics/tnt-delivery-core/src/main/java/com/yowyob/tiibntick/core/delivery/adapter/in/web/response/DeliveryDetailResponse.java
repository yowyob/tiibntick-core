package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * HTTP response DTO for a {@code Delivery} aggregate.
 *
 * @author MANFOUO Braun
 */
public record DeliveryDetailResponse(
        UUID id,
        UUID tenantId,
        UUID announcementId,
        UUID senderId,
        UUID deliveryPersonId,
        DeliveryStatus status,
        DeliveryUrgency urgency,
        String pickupDisplay,
        String deliveryDisplay,
        String recipientName,
        String recipientPhone,
        BigDecimal estimatedCostTotal,
        String currency,
        double estimatedDistanceKm,
        Instant etaEstimatedArrival,
        double etaConfidence,
        int etaRemainingMinutes,
        Instant scheduledPickupTime,
        Instant estimatedDeliveryTime,
        Instant actualPickupTime,
        Instant actualDeliveryTime,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
