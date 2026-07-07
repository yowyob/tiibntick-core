package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeEntity;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeEvidenceEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pure mapper between the {@link Dispute} domain aggregate and {@link DisputeEntity}.
 * No Spring injection — stateless, all static methods.
 *
 * @author MANFOUO Braun
 */
public final class DisputePersistenceMapper {

    private DisputePersistenceMapper() {}

    /**
     * Converts a {@link Dispute} aggregate into a persistable {@link DisputeEntity}.
     *
     * @param dispute the domain aggregate
     * @return the persistence entity
     */
    public static DisputeEntity toEntity(final Dispute dispute) {
        final DisputeEntity entity = new DisputeEntity();
        entity.setId(dispute.getId().getValue());
        entity.setTenantId(dispute.getTenantId());
        entity.setReference(dispute.getReference().getValue());
        entity.setCause(dispute.getCause().name());
        entity.setCategory(dispute.getCategory().name());
        entity.setPriority(dispute.getPriority().name());
        entity.setStatus(dispute.getStatus().name());
        entity.setClaimantId(dispute.getClaimantId());
        entity.setClaimantType(dispute.getClaimantType().name());
        entity.setRespondentId(dispute.getRespondentId());
        entity.setRespondentType(dispute.getRespondentType().name());
        entity.setMissionId(dispute.getMissionId());
        entity.setPackageId(dispute.getPackageId());
        entity.setTrackingCode(dispute.getTrackingCode());
        entity.setDescription(dispute.getDescription());
        entity.setFiledAt(dispute.getFiledAt());
        entity.setDeadline(dispute.getDeadline());
        entity.setAssignedMediatorId(dispute.getAssignedMediatorId());
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(dispute.getFiledAt());
        }
        entity.setVersion(dispute.getVersion());
        //  FreelancerOrg context
        entity.setRespondentOrgId(dispute.getRespondentOrgId());
        entity.setImpliedSubDelivererId(dispute.getImpliedSubDelivererId());
        entity.setSubDelivererInvolved(dispute.getSubDelivererInvolved());

        // Resolution
        if (dispute.getResolution() != null) {
            final DisputeResolution r = dispute.getResolution();
            entity.setResolutionType(r.getType().name());
            entity.setResolutionCompensationRequired(r.isCompensationRequired());
            entity.setResolutionMediatorId(r.getMediatorId());
            entity.setResolutionSummary(r.getSummary());
            entity.setResolutionOccurredAt(r.getOccurredAt());
        }

        // Compensation
        if (dispute.getCompensation() != null) {
            final CompensationDetails c = dispute.getCompensation();
            entity.setCompensationAmount(c.getAmount());
            entity.setCompensationCurrency(c.getCurrency());
            entity.setCompensationMethod(c.getMethod().name());
            entity.setCompensationBeneficiaryId(c.getBeneficiaryId());
            entity.setCompensationPaymentReference(c.getPaymentReference());
            entity.setCompensationApprovedAt(c.getApprovedAt());
            entity.setCompensationPaidAt(c.getPaidAt());
        }

        // SLA
        final DisputeSLAPolicy sla = dispute.getSlaPolicy();
        entity.setSlaResponseHours(sla.getInitialResponseDeadlineHours());
        entity.setSlaInvestigationDays(sla.getInvestigationDeadlineDays());
        entity.setSlaResolutionDays(sla.getResolutionDeadlineDays());
        entity.setSlaEscalationDays(sla.getEscalationThresholdDays());

        return entity;
    }

    /**
     * Reconstitutes a {@link Dispute} aggregate from persisted data.
     *
     * @param entity    the dispute entity row
     * @param evidences the associated evidence entities (loaded separately)
     * @return the reconstituted domain aggregate
     */
    public static Dispute toDomain(
            final DisputeEntity entity,
            final List<DisputeEvidence> evidences) {

        final DisputeResolution resolution = buildResolution(entity);
        final CompensationDetails compensation = buildCompensation(entity);
        final DisputeSLAPolicy slaPolicy = DisputeSLAPolicy.custom(
                entity.getSlaResponseHours(),
                entity.getSlaInvestigationDays(),
                entity.getSlaResolutionDays(),
                entity.getSlaEscalationDays());

        return Dispute.reconstituteFull(
                DisputeId.of(entity.getId()),
                entity.getTenantId(),
                DisputeReference.of(entity.getReference()),
                DisputeCause.valueOf(entity.getCause()),
                DisputeCategory.valueOf(entity.getCategory()),
                DisputePriority.valueOf(entity.getPriority()),
                DisputeStatus.valueOf(entity.getStatus()),
                entity.getClaimantId(),
                ClaimantType.valueOf(entity.getClaimantType()),
                entity.getRespondentId(),
                RespondentType.valueOf(entity.getRespondentType()),
                entity.getMissionId(),
                entity.getPackageId(),
                entity.getTrackingCode(),
                entity.getDescription(),
                entity.getFiledAt(),
                entity.getDeadline(),
                entity.getAssignedMediatorId(),
                resolution,
                compensation,
                evidences,
                List.of(),  // timeline events loaded lazily
                List.of(),  // comments loaded lazily
                List.of(),  // escalation history loaded lazily
                slaPolicy,
                entity.getVersion() != null ? entity.getVersion() : 0,
                entity.getRespondentOrgId(),
                entity.getImpliedSubDelivererId(),
                entity.getSubDelivererInvolved());
    }

    /**
     * Converts a {@link DisputeEvidenceEntity} to a {@link DisputeEvidence} domain entity.
     *
     * @param e the persistence entity
     * @return the domain evidence entity
     */
    public static DisputeEvidence evidenceToDomain(final DisputeEvidenceEntity e) {
        return DisputeEvidence.reconstitute(
                EvidenceId.of(e.getId()),
                DisputeId.of(e.getDisputeId()),
                e.getSubmittedBy(),
                EvidenceSubmitterType.valueOf(e.getSubmitterType()),
                EvidenceType.valueOf(e.getEvidenceType()),
                e.getFileKey(),
                e.getDescription(),
                e.getSubmittedAt(),
                e.isVerified(),
                e.getVerifiedAt(),
                e.getVerifiedByMediatorId(),
                e.getBlockchainRef());
    }

    /**
     * Converts a {@link DisputeEvidence} domain entity to a {@link DisputeEvidenceEntity}.
     *
     * @param evidence the domain entity
     * @param tenantId the tenant scope (denormalized for efficient querying)
     * @return the persistence entity
     */
    public static DisputeEvidenceEntity evidenceToEntity(final DisputeEvidence evidence, final String tenantId) {
        final DisputeEvidenceEntity e = new DisputeEvidenceEntity();
        e.setId(evidence.getId().getValue());
        e.setDisputeId(evidence.getDisputeId().getValue());
        e.setSubmittedBy(evidence.getSubmittedBy());
        e.setSubmitterType(evidence.getSubmitterType().name());
        e.setEvidenceType(evidence.getType().name());
        e.setFileKey(evidence.getFileKey());
        e.setDescription(evidence.getDescription());
        e.setSubmittedAt(evidence.getSubmittedAt());
        e.setVerified(evidence.isVerified());
        e.setVerifiedAt(evidence.getVerifiedAt());
        e.setVerifiedByMediatorId(evidence.getVerifiedByMediatorId());
        e.setBlockchainRef(evidence.getBlockchainRef());
        e.setTenantId(tenantId);
        return e;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private static DisputeResolution buildResolution(final DisputeEntity entity) {
        if (entity.getResolutionType() == null) return null;
        return DisputeResolution.of(
                ResolutionType.valueOf(entity.getResolutionType()),
                Boolean.TRUE.equals(entity.getResolutionCompensationRequired()),
                entity.getResolutionMediatorId(),
                entity.getResolutionSummary());
    }

    private static CompensationDetails buildCompensation(final DisputeEntity entity) {
        if (entity.getCompensationAmount() == null) return null;
        CompensationDetails c = CompensationDetails.approved(
                entity.getCompensationAmount(),
                entity.getCompensationCurrency(),
                CompensationMethod.valueOf(entity.getCompensationMethod()),
                entity.getCompensationBeneficiaryId());
        if (entity.getCompensationPaymentReference() != null) {
            c = c.markAsPaid(entity.getCompensationPaymentReference());
        }
        return c;
    }
}
