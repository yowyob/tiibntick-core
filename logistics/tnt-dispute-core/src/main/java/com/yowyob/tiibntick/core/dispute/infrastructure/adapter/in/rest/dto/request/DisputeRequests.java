package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request;

import java.math.BigDecimal;

public final class DisputeRequests {

    private DisputeRequests() {}

    public record OpenDisputeRequest(
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
            String respondentOrgId,
            String impliedSubDelivererId,
            Boolean subDelivererInvolved) {}

    public record AssignMediatorRequest(String mediatorId) {}

    public record AddEvidenceRequest(
            String submittedBy,
            String submitterType,
            String evidenceType,
            String fileKey,
            String description) {}

    public record RequestEvidenceRequest(
            String requestedFrom,
            String deadline,
            String reason) {}

    public record RuleDisputeRequest(
            String resolutionType,
            boolean compensationRequired,
            String mediatorId,
            String summary,
            BigDecimal compensationAmount,
            String compensationCurrency,
            String compensationMethod,
            String beneficiaryId) {}

    public record EscalateDisputeRequest(
            String escalatedBy,
            String reason,
            String assignedTo) {}

    public record ProcessCompensationRequest(String paymentReference) {}

    public record CloseDisputeRequest(
            String closureType,
            String summary,
            String closedBy) {}

    public record WithdrawDisputeRequest(String claimantId) {}

    public record AddCommentRequest(
            String authorId,
            String authorType,
            String content,
            boolean isInternal) {}

    public record VerifyEvidenceRequest(String mediatorId) {}
}
