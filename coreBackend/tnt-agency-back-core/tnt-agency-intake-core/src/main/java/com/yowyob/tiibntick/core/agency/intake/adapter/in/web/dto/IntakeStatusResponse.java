package com.yowyob.tiibntick.core.agency.intake.adapter.in.web.dto;

import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest;

import java.time.Instant;
import java.util.UUID;

public record IntakeStatusResponse(
        UUID id, String referenceCode, String status, String source,
        String senderName, String recipientName,
        String deliveryAddress, String deliveryMode,
        String trackingCode, UUID missionId,
        String agencyName, String branchName,
        String rejectionReason, Instant reviewedAt, Instant createdAt) {

    public static IntakeStatusResponse from(
            ClientIntakeRequest r, String agencyName, String branchName) {
        return new IntakeStatusResponse(
                r.getId(), r.getReferenceCode(), r.getStatus().name(), r.getSource().name(),
                r.getSenderName(), r.getRecipientName(),
                r.getDeliveryAddress(), r.getDeliveryMode().name(),
                r.getTrackingCode(), r.getMissionId(),
                agencyName, branchName,
                r.getRejectionReason(), r.getReviewedAt(), r.getCreatedAt());
    }
}
