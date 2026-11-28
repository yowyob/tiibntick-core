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
        String trackingCode,
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
        Instant updatedAt,
        // lot evt-l2 (spec §1.1, §1.2, §7.1): appended at the end, existing fields unchanged
        String kind,
        UUID parentDeliveryId,
        String parcelStatus,
        int failedAttempts,
        int maxAttempts,
        String failureReason,
        String lastAttemptReason,
        UUID relayHubId,
        Integer relayStorageDays,
        Instant relayStorageExpiresAt,
        boolean late,
        // D-147, D-151: return guarantee of a delivery to a relay hub (null when none is concerned)
        String returnGuaranteeStatus,
        String returnGuaranteeReason
) {}
