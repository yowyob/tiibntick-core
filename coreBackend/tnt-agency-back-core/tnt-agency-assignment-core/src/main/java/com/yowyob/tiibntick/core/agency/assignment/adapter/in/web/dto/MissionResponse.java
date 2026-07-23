package com.yowyob.tiibntick.core.agency.assignment.adapter.in.web.dto;

import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MissionResponse(
        UUID id,
        UUID tenantId,
        UUID agencyId,
        UUID coreMissionId,
        UUID assignedDelivererId,
        UUID assignedVehicleId,
        String status,
        Instant scheduledAt,
        BigDecimal quotedAmount,
        String quotedCurrency,
        UUID branchId,
        String pickupAddress,
        String deliveryAddress,
        String senderName,
        String recipientName,
        String recipientPhone,
        Double weightKg,
        Double distanceKm,
        UUID targetHubId,
        Instant createdAt,
        Instant updatedAt
) {
    public static MissionResponse from(AgencyMission m) {
        return new MissionResponse(
                m.getId(), m.getTenantId(), m.getAgencyId(), m.getCoreMissionId(),
                m.getAssignedDelivererId(), m.getAssignedVehicleId(),
                m.getStatus().name(), m.getScheduledAt(),
                m.getQuotedAmount(), m.getQuotedCurrency(),
                m.getBranchId(), m.getPickupAddress(), m.getDeliveryAddress(),
                m.getSenderName(), m.getRecipientName(), m.getRecipientPhone(),
                m.getWeightKg(), m.getDistanceKm(), m.getTargetHubId(),
                m.getCreatedAt(), m.getUpdatedAt());
    }
}
