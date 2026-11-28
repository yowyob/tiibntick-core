package com.yowyob.tiibntick.core.delivery.domain.event;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Unmasked state of a delivery after a change (spec §3.6 {@code delivery.payload}). The addresses and the recipient
 * are full here; the publisher masks them ({@code TntEventMasking}) before anything leaves the process.
 */
public record DeliverySnapshot(
        UUID deliveryId,
        UUID parcelId,
        DeliveryKind kind,
        UUID parentDeliveryId,
        DeliveryStatus status,
        DeliveryStatus previousStatusBeforeIncident,
        UUID announcementId,
        UUID agencyId,
        String platform,
        UUID senderId,
        RecipientInfo recipient,
        UUID courierId,
        String vehicleId,
        String freelancerOrgId,
        DeliveryAddress pickupAddress,
        DeliveryAddress deliveryAddress,
        UUID relayHubId,
        Instant pickupWindowStart,
        Instant pickupWindowEnd,
        Instant deliveryWindowStart,
        Instant deliveryWindowEnd,
        Instant eta,
        int attemptCount,
        int maxAttempts,
        DeliveryFailureReason failureReason,
        DeliveryFailureReason lastAttemptReason,
        String cancellationReason,
        ResponsibleParty returnCostPayer,
        boolean late,
        Instant lateSince,
        String lastAlertType,
        UUID openIncidentId,
        boolean frozenByDispute,
        BigDecimal agreedAmount,
        String currency,
        Instant relayStorageExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt,
        Instant failedAt,
        Instant cancelledAt,
        java.util.List<String> activeAlerts) {
}
