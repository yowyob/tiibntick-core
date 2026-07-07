package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DisputeResponses {

    private DisputeResponses() {}

    public record DisputeSummaryResponse(
            String id,
            String reference,
            String tenantId,
            String status,
            String cause,
            String category,
            String priority,
            String claimantId,
            String claimantType,
            String respondentId,
            String respondentType,
            String missionId,
            String packageId,
            String trackingCode,
            LocalDateTime filedAt,
            LocalDateTime deadline,
            String assignedMediatorId,
            int evidenceCount) {}

    public record DisputeDetailResponse(
            String id,
            String reference,
            String tenantId,
            String status,
            String cause,
            String category,
            String priority,
            String claimantId,
            String claimantType,
            String respondentId,
            String respondentType,
            String missionId,
            String packageId,
            String trackingCode,
            String description,
            LocalDateTime filedAt,
            LocalDateTime deadline,
            String assignedMediatorId,
            ResolutionResponse resolution,
            CompensationResponse compensation,
            List<EvidenceResponse> evidences,
            SlaResponse sla) {}

    public record ResolutionResponse(
            String type,
            boolean compensationRequired,
            String mediatorId,
            String summary,
            LocalDateTime occurredAt) {}

    public record CompensationResponse(
            BigDecimal amount,
            String currency,
            String method,
            String beneficiaryId,
            String paymentReference,
            LocalDateTime approvedAt,
            LocalDateTime paidAt,
            boolean paid,
            String formatted) {}

    public record EvidenceResponse(
            String id,
            String disputeId,
            String submittedBy,
            String submitterType,
            String type,
            String fileKey,
            String description,
            LocalDateTime submittedAt,
            boolean verified,
            LocalDateTime verifiedAt,
            String verifiedByMediatorId,
            String blockchainRef) {}

    public record SlaResponse(
            int responseHours,
            int investigationDays,
            int resolutionDays,
            LocalDateTime responseDeadline,
            LocalDateTime resolutionDeadline,
            boolean responseBreached,
            boolean resolutionBreached) {}

    public record DisputePageResponse(
            List<DisputeSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            boolean hasNext,
            boolean hasPrevious) {}

    public record DisputeOpenedResponse(
            String id,
            String reference,
            String status,
            String priority,
            LocalDateTime filedAt,
            LocalDateTime deadline) {}
}
