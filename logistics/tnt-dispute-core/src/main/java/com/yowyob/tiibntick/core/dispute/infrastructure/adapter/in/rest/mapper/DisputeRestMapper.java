package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.mapper;

import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request.DisputeRequests;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.response.DisputeResponses.*;

import java.time.LocalDateTime;

public final class DisputeRestMapper {

    private DisputeRestMapper() {}

    public static OpenDisputeCommand toCommand(DisputeRequests.OpenDisputeRequest req, String tenantId) {
        return new OpenDisputeCommand(
                tenantId,
                req.claimantId(),
                ClaimantType.valueOf(req.claimantType()),
                req.respondentId(),
                RespondentType.valueOf(req.respondentType()),
                DisputeCause.valueOf(req.cause()),
                DisputeCategory.valueOf(req.category()),
                req.priority() != null ? DisputePriority.valueOf(req.priority()) : DisputePriority.NORMAL,
                req.missionId(),
                req.packageId(),
                req.trackingCode(),
                req.description(),
                req.respondentOrgId(),
                req.impliedSubDelivererId(),
                req.subDelivererInvolved());
    }

    public static AssignMediatorCommand toCommand(DisputeRequests.AssignMediatorRequest req, String disputeId, String tenantId) {
        return new AssignMediatorCommand(DisputeId.of(disputeId), tenantId, req.mediatorId(), "SYSTEM");
    }

    public static AddEvidenceCommand toCommand(DisputeRequests.AddEvidenceRequest req, String disputeId, String tenantId) {
        return new AddEvidenceCommand(
                DisputeId.of(disputeId),
                tenantId,
                req.submittedBy(),
                EvidenceSubmitterType.valueOf(req.submitterType()),
                EvidenceType.valueOf(req.evidenceType()),
                req.fileKey(),
                req.description(),
                req.evidenceHash());
    }

    public static RequestEvidenceCommand toCommand(DisputeRequests.RequestEvidenceRequest req, String disputeId, String tenantId, String requestedBy) {
        LocalDateTime deadline = req.deadline() != null ? LocalDateTime.parse(req.deadline()) : LocalDateTime.now().plusDays(3);
        return new RequestEvidenceCommand(DisputeId.of(disputeId), tenantId, req.requestedFrom(), requestedBy, deadline, req.reason());
    }

    public static RuleDisputeCommand toCommand(DisputeRequests.RuleDisputeRequest req, String disputeId, String tenantId, String ruledBy) {
        CompensationDetails compensation = null;
        if (req.compensationRequired() && req.compensationAmount() != null) {
            compensation = CompensationDetails.approved(
                    req.compensationAmount(),
                    req.compensationCurrency(),
                    CompensationMethod.valueOf(req.compensationMethod()),
                    req.beneficiaryId());
        }
        return new RuleDisputeCommand(
                DisputeId.of(disputeId),
                tenantId,
                ruledBy,
                ResolutionType.valueOf(req.resolutionType()),
                req.compensationRequired(),
                compensation,
                req.summary());
    }

    public static EscalateDisputeCommand toCommand(DisputeRequests.EscalateDisputeRequest req, String disputeId, String tenantId) {
        return new EscalateDisputeCommand(DisputeId.of(disputeId), tenantId, req.escalatedBy(), req.reason(), req.assignedTo());
    }

    public static ProcessCompensationCommand toCommand(DisputeRequests.ProcessCompensationRequest req, String disputeId, String tenantId) {
        return new ProcessCompensationCommand(DisputeId.of(disputeId), tenantId, req.paymentReference(), "SYSTEM");
    }

    public static CloseDisputeCommand toCommand(DisputeRequests.CloseDisputeRequest req, String disputeId, String tenantId) {
        return new CloseDisputeCommand(
                DisputeId.of(disputeId),
                tenantId,
                req.closedBy(),
                ClosureType.valueOf(req.closureType()),
                req.summary());
    }

    public static WithdrawDisputeCommand toCommand(DisputeRequests.WithdrawDisputeRequest req, String disputeId, String tenantId) {
        return new WithdrawDisputeCommand(DisputeId.of(disputeId), tenantId, req.claimantId());
    }

    public static AddCommentCommand toCommand(DisputeRequests.AddCommentRequest req, String disputeId, String tenantId) {
        return new AddCommentCommand(
                DisputeId.of(disputeId),
                tenantId,
                req.authorId(),
                CommentAuthorType.valueOf(req.authorType()),
                req.content(),
                req.isInternal());
    }

    public static DisputeOpenedResponse toOpenedResponse(Dispute dispute) {
        return new DisputeOpenedResponse(
                dispute.getId().getValue(),
                dispute.getReference().getValue(),
                dispute.getStatus().name(),
                dispute.getPriority().name(),
                dispute.getFiledAt(),
                dispute.getDeadline());
    }

    public static DisputeDetailResponse toDetailResponse(Dispute dispute) {
        return new DisputeDetailResponse(
                dispute.getId().getValue(),
                dispute.getReference().getValue(),
                dispute.getTenantId(),
                dispute.getStatus().name(),
                dispute.getCause().name(),
                dispute.getCategory().name(),
                dispute.getPriority().name(),
                dispute.getClaimantId(),
                dispute.getClaimantType().name(),
                dispute.getRespondentId(),
                dispute.getRespondentType().name(),
                dispute.getMissionId(),
                dispute.getPackageId(),
                dispute.getTrackingCode(),
                dispute.getDescription(),
                dispute.getFiledAt(),
                dispute.getDeadline(),
                dispute.getAssignedMediatorId(),
                toResolutionResponse(dispute.getResolution()),
                toCompensationResponse(dispute.getCompensation()),
                dispute.getEvidences().stream().map(DisputeRestMapper::toEvidenceResponse).toList(),
                toSlaResponse(dispute.getSlaPolicy(), dispute.getFiledAt()));
    }

    public static DisputeSummaryResponse toSummaryResponse(Dispute dispute) {
        return new DisputeSummaryResponse(
                dispute.getId().getValue(),
                dispute.getReference().getValue(),
                dispute.getTenantId(),
                dispute.getStatus().name(),
                dispute.getCause().name(),
                dispute.getCategory().name(),
                dispute.getPriority().name(),
                dispute.getClaimantId(),
                dispute.getClaimantType().name(),
                dispute.getRespondentId(),
                dispute.getRespondentType().name(),
                dispute.getMissionId(),
                dispute.getPackageId(),
                dispute.getTrackingCode(),
                dispute.getFiledAt(),
                dispute.getDeadline(),
                dispute.getAssignedMediatorId(),
                dispute.getEvidences().size());
    }

    public static EvidenceResponse toEvidenceResponse(DisputeEvidence e) {
        return new EvidenceResponse(
                e.getId().getValue(),
                e.getDisputeId().getValue(),
                e.getSubmittedBy(),
                e.getSubmitterType().name(),
                e.getType().name(),
                e.getFileKey(),
                e.getDescription(),
                e.getSubmittedAt(),
                e.isVerified(),
                e.getVerifiedAt(),
                e.getVerifiedByMediatorId(),
                e.getBlockchainRef(),
                e.getEvidenceHash());
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private static ResolutionResponse toResolutionResponse(DisputeResolution r) {
        if (r == null) return null;
        return new ResolutionResponse(r.getType().name(), r.isCompensationRequired(), r.getMediatorId(),
                r.getSummary(), r.getOccurredAt());
    }

    private static CompensationResponse toCompensationResponse(CompensationDetails c) {
        if (c == null) return null;
        return new CompensationResponse(c.getAmount(), c.getCurrency(), c.getMethod().name(),
                c.getBeneficiaryId(), c.getPaymentReference(), c.getApprovedAt(), c.getPaidAt(),
                c.isPaid(), c.formattedAmount());
    }

    private static SlaResponse toSlaResponse(DisputeSLAPolicy sla, LocalDateTime filedAt) {
        LocalDateTime now = LocalDateTime.now();
        return new SlaResponse(
                sla.getInitialResponseDeadlineHours(),
                sla.getInvestigationDeadlineDays(),
                sla.getResolutionDeadlineDays(),
                sla.responseDeadline(filedAt),
                sla.resolutionDeadline(filedAt),
                sla.isResponseBreached(filedAt, now),
                sla.isResolutionBreached(filedAt, now));
    }
}
