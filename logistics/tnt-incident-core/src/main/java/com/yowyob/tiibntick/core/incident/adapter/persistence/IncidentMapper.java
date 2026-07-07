package com.yowyob.tiibntick.core.incident.adapter.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.incident.adapter.persistence.entity.*;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.*;
import com.yowyob.tiibntick.core.incident.domain.valueobject.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bidirectional mapper between Incident domain objects and their R2DBC persistence entities.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Component
@RequiredArgsConstructor
public class IncidentMapper {

    private final ObjectMapper objectMapper;

    public IncidentEntity toEntity(Incident d) {
        var entity = IncidentEntity.builder()
                .id(d.getId())
                .referenceCode(d.getReferenceCode())
                .tenantId(d.getTenantId())
                .agencyId(d.getAgencyId())
                .sourcePlatform(d.getSourcePlatform() != null ? d.getSourcePlatform().name() : null)
                .missionId(d.getMissionId())
                .category(d.getCategory() != null ? d.getCategory().name() : null)
                .type(d.getType() != null ? d.getType().name() : null)
                .severity(d.getSeverity() != null ? d.getSeverity().name() : null)
                .status(d.getStatus() != null ? d.getStatus().name() : null)
                .resolutionMode(d.getResolutionMode() != null ? d.getResolutionMode().name() : null)
                .reportedByActorId(d.getReportedByActorId())
                .reportedByRole(d.getReportedByRole() != null ? d.getReportedByRole().name() : null)
                .description(d.getDescription())
                .affectedParcelIds(serializeUUIDs(d.getAffectedParcelIds()))
                .multiParcelIncident(d.isMultiParcelIncident())
                .ownBlockchainChainId(d.getOwnBlockchainChainId())
                .reportedAt(d.getReportedAt())
                .detectedAt(d.getDetectedAt())
                .acknowledgedAt(d.getAcknowledgedAt())
                .triagedAt(d.getTriagedAt())
                .resolvedAt(d.getResolvedAt())
                .closedAt(d.getClosedAt())
                .lastEscalationLevel(d.getLastEscalationLevel())
                .autoResolutionAttempts(d.getAutoResolutionAttempts())
                .interAgencyInvolved(d.isInterAgencyInvolved())
                .version(d.getVersion())
                //  FreelancerOrg context
                .responsibleOrgId(d.getResponsibleOrgId())
                .responsibleOrgType(d.getResponsibleOrgType())
                .build();

        if (d.getGeoSnapshot() != null) {
            entity.setGeoLat(d.getGeoSnapshot().getLatitude());
            entity.setGeoLng(d.getGeoSnapshot().getLongitude());
            entity.setGeoAddressLabel(d.getGeoSnapshot().getAddressLabel());
            entity.setGeoNearestHubId(d.getGeoSnapshot().getNearestHubId());
            entity.setGeoZoneRiskIndex(d.getGeoSnapshot().getZoneRiskIndex());
        }
        if (d.getSlaImpact() != null) {
            entity.setSlaOriginalDeadline(d.getSlaImpact().getOriginalSlaDeadline());
            entity.setSlaEstimatedDelayMinutes(d.getSlaImpact().getEstimatedDelayMinutes());
            entity.setSlaRevisedDeadline(d.getSlaImpact().getRevisedDeadline());
            entity.setSlaBreached(d.getSlaImpact().isSlaBreached());
            entity.setSlaBreachMinutes(d.getSlaImpact().getBreachMinutes());
        }
        if (d.getRiskScore() != null) {
            entity.setRiskGlobalScore(d.getRiskScore().getGlobalScore());
            entity.setRiskAutoResolutionRecommended(d.getRiskScore().isAutoResolutionRecommended());
            entity.setRiskRecommendedAction(d.getRiskScore().getRecommendedAction());
        }
        if (d.getCompensationImpact() != null) {
            entity.setCompensationEstimatedXaf(d.getCompensationImpact().getEstimatedAmountXAF());
            entity.setCompensationCoverageType(d.getCompensationImpact().getCoverageType() != null
                    ? d.getCompensationImpact().getCoverageType().name() : null);
        }
        return entity;
    }

    public Incident toDomain(IncidentEntity e) {
        IncidentGeoSnapshot geo = null;
        if (e.getGeoLat() != null) {
            geo = IncidentGeoSnapshot.builder()
                    .incidentId(e.getId())
                    .latitude(e.getGeoLat())
                    .longitude(e.getGeoLng() != null ? e.getGeoLng() : 0.0)
                    .addressLabel(e.getGeoAddressLabel())
                    .nearestHubId(e.getGeoNearestHubId())
                    .zoneRiskIndex(e.getGeoZoneRiskIndex() != null ? e.getGeoZoneRiskIndex() : 0.0)
                    .build();
        }
        IncidentSlaImpact sla = null;
        if (e.getSlaOriginalDeadline() != null) {
            sla = IncidentSlaImpact.builder()
                    .incidentId(e.getId())
                    .originalSlaDeadline(e.getSlaOriginalDeadline())
                    .estimatedDelayMinutes(e.getSlaEstimatedDelayMinutes() != null ? e.getSlaEstimatedDelayMinutes() : 0L)
                    .revisedDeadline(e.getSlaRevisedDeadline())
                    .slaBreached(e.getSlaBreached() != null && e.getSlaBreached())
                    .breachMinutes(e.getSlaBreachMinutes() != null ? e.getSlaBreachMinutes() : 0L)
                    .penaltyApplicable(e.getSlaBreachMinutes() != null && e.getSlaBreachMinutes() > 30)
                    .build();
        }

        return Incident.builder()
                .id(e.getId())
                .referenceCode(e.getReferenceCode())
                .tenantId(e.getTenantId())
                .agencyId(e.getAgencyId())
                .sourcePlatform(e.getSourcePlatform() != null ? PlatformType.valueOf(e.getSourcePlatform()) : null)
                .missionId(e.getMissionId())
                .category(e.getCategory() != null ? IncidentCategory.valueOf(e.getCategory()) : null)
                .type(e.getType() != null ? IncidentType.valueOf(e.getType()) : null)
                .severity(e.getSeverity() != null ? IncidentSeverity.valueOf(e.getSeverity()) : null)
                .status(e.getStatus() != null ? IncidentStatus.valueOf(e.getStatus()) : null)
                .resolutionMode(e.getResolutionMode() != null ? ResolutionMode.valueOf(e.getResolutionMode()) : null)
                .reportedByActorId(e.getReportedByActorId())
                .reportedByRole(e.getReportedByRole() != null ? ActorRole.valueOf(e.getReportedByRole()) : null)
                .description(e.getDescription())
                .affectedParcelIds(deserializeUUIDs(e.getAffectedParcelIds()))
                .multiParcelIncident(e.isMultiParcelIncident())
                .ownBlockchainChainId(e.getOwnBlockchainChainId())
                .reportedAt(e.getReportedAt())
                .detectedAt(e.getDetectedAt())
                .acknowledgedAt(e.getAcknowledgedAt())
                .triagedAt(e.getTriagedAt())
                .resolvedAt(e.getResolvedAt())
                .closedAt(e.getClosedAt())
                .lastEscalationLevel(e.getLastEscalationLevel())
                .autoResolutionAttempts(e.getAutoResolutionAttempts())
                .interAgencyInvolved(e.isInterAgencyInvolved())
                .geoSnapshot(geo)
                .slaImpact(sla)
                .version(e.getVersion())
                //  FreelancerOrg context
                .responsibleOrgId(e.getResponsibleOrgId())
                .responsibleOrgType(e.getResponsibleOrgType())
                .build();
    }

    public IncidentEventLogEntity toEntity(IncidentEventLog d) {
        return IncidentEventLogEntity.builder()
                .id(d.getId()).incidentId(d.getIncidentId())
                .eventType(d.getEventType()).occurredAt(d.getOccurredAt())
                .performedByActorId(d.getPerformedByActorId())
                .performedByRole(d.getPerformedByRole() != null ? d.getPerformedByRole().name() : null)
                .payload(d.getPayload()).blockchainTxHash(d.getBlockchainTxHash())
                .blockchainChainRef(d.getBlockchainChainRef())
                .writtenOnParcelChain(d.isWrittenOnParcelChain())
                .writtenOnIncidentChain(d.isWrittenOnIncidentChain())
                .build();
    }

    public IncidentEventLog toDomain(IncidentEventLogEntity e) {
        return IncidentEventLog.builder()
                .id(e.getId()).incidentId(e.getIncidentId())
                .eventType(e.getEventType()).occurredAt(e.getOccurredAt())
                .performedByActorId(e.getPerformedByActorId())
                .performedByRole(e.getPerformedByRole() != null ? ActorRole.valueOf(e.getPerformedByRole()) : null)
                .payload(e.getPayload()).blockchainTxHash(e.getBlockchainTxHash())
                .blockchainChainRef(e.getBlockchainChainRef())
                .writtenOnParcelChain(e.isWrittenOnParcelChain())
                .writtenOnIncidentChain(e.isWrittenOnIncidentChain())
                .build();
    }

    public IncidentEvidenceEntity toEntity(IncidentEvidence d) {
        return IncidentEvidenceEntity.builder()
                .id(d.getId()).incidentId(d.getIncidentId())
                .evidenceType(d.getEvidenceType() != null ? d.getEvidenceType().name() : null)
                .fileUrl(d.getFileUrl()).mimeType(d.getMimeType())
                .capturedAt(d.getCapturedAt()).capturedByActorId(d.getCapturedByActorId())
                .capturedByRole(d.getCapturedByRole() != null ? d.getCapturedByRole().name() : null)
                .validated(d.isValidated()).validatedAt(d.getValidatedAt())
                .validatedByActorId(d.getValidatedByActorId())
                .blockchainTxHash(d.getBlockchainTxHash())
                .sha256Checksum(d.getSha256Checksum())
                .latitude(d.getLatitude()).longitude(d.getLongitude())
                .build();
    }

    public IncidentEvidence toDomain(IncidentEvidenceEntity e) {
        return IncidentEvidence.builder()
                .id(e.getId()).incidentId(e.getIncidentId())
                .evidenceType(e.getEvidenceType() != null ? EvidenceType.valueOf(e.getEvidenceType()) : null)
                .fileUrl(e.getFileUrl()).mimeType(e.getMimeType())
                .capturedAt(e.getCapturedAt()).capturedByActorId(e.getCapturedByActorId())
                .capturedByRole(e.getCapturedByRole() != null ? ActorRole.valueOf(e.getCapturedByRole()) : null)
                .validated(e.isValidated()).validatedAt(e.getValidatedAt())
                .validatedByActorId(e.getValidatedByActorId())
                .blockchainTxHash(e.getBlockchainTxHash())
                .sha256Checksum(e.getSha256Checksum())
                .latitude(e.getLatitude()).longitude(e.getLongitude())
                .build();
    }

    public IncidentDriverReplacementEntity toEntity(IncidentDriverReplacement d) {
        var builder = IncidentDriverReplacementEntity.builder()
                .id(d.getId()).incidentId(d.getIncidentId())
                .originalDriverId(d.getOriginalDriverId()).originalVehicleId(d.getOriginalVehicleId())
                .replacementDriverId(d.getReplacementDriverId()).replacementVehicleId(d.getReplacementVehicleId())
                .replacementAgencyId(d.getReplacementAgencyId())
                .handoverLatitude(d.getHandoverLatitude()).handoverLongitude(d.getHandoverLongitude())
                .handoverAddress(d.getHandoverAddress()).handoverScheduledAt(d.getHandoverScheduledAt())
                .handoverAt(d.getHandoverAt())
                .handoverStatus(d.getHandoverStatus() != null ? d.getHandoverStatus().name() : null)
                .originalDriverConfirmedAt(d.getOriginalDriverConfirmedAt())
                .replacementDriverConfirmedAt(d.getReplacementDriverConfirmedAt())
                .blockchainTxHash(d.getBlockchainTxHash());
        if (d.getPricingAdjustment() != null) {
            builder.originalPriceXaf(d.getPricingAdjustment().getOriginalPriceXAF())
                    .adjustedPriceXaf(d.getPricingAdjustment().getAdjustedPriceXAF())
                    .extraKmFee(d.getPricingAdjustment().getExtraKmFee())
                    .urgencyFee(d.getPricingAdjustment().getUrgencyFee())
                    .pricingReason(d.getPricingAdjustment().getAdjustmentReason());
        }
        return builder.build();
    }

    public IncidentDriverReplacement toDomain(IncidentDriverReplacementEntity e) {
        PricingAdjustment pricing = null;
        if (e.getOriginalPriceXaf() != null) {
            pricing = PricingAdjustment.builder()
                    .originalPriceXAF(e.getOriginalPriceXaf() != null ? e.getOriginalPriceXaf() : BigDecimal.ZERO)
                    .adjustedPriceXAF(e.getAdjustedPriceXaf() != null ? e.getAdjustedPriceXaf() : BigDecimal.ZERO)
                    .extraKmFee(e.getExtraKmFee() != null ? e.getExtraKmFee() : BigDecimal.ZERO)
                    .urgencyFee(e.getUrgencyFee() != null ? e.getUrgencyFee() : BigDecimal.ZERO)
                    .adjustmentReason(e.getPricingReason())
                    .build();
        }
        return IncidentDriverReplacement.builder()
                .id(e.getId()).incidentId(e.getIncidentId())
                .originalDriverId(e.getOriginalDriverId()).originalVehicleId(e.getOriginalVehicleId())
                .replacementDriverId(e.getReplacementDriverId()).replacementVehicleId(e.getReplacementVehicleId())
                .replacementAgencyId(e.getReplacementAgencyId())
                .handoverLatitude(e.getHandoverLatitude()).handoverLongitude(e.getHandoverLongitude())
                .handoverAddress(e.getHandoverAddress()).handoverScheduledAt(e.getHandoverScheduledAt())
                .handoverAt(e.getHandoverAt())
                .handoverStatus(e.getHandoverStatus() != null ? HandoverStatus.valueOf(e.getHandoverStatus()) : null)
                .originalDriverConfirmedAt(e.getOriginalDriverConfirmedAt())
                .replacementDriverConfirmedAt(e.getReplacementDriverConfirmedAt())
                .pricingAdjustment(pricing).blockchainTxHash(e.getBlockchainTxHash())
                .build();
    }

    public IncidentInterAgencyCooperationEntity toEntity(IncidentInterAgencyCooperation d) {
        return IncidentInterAgencyCooperationEntity.builder()
                .id(d.getId()).incidentId(d.getIncidentId())
                .requestingAgencyId(d.getRequestingAgencyId()).respondingAgencyId(d.getRespondingAgencyId())
                .cooperationType(d.getCooperationType() != null ? d.getCooperationType().name() : null)
                .status(d.getStatus() != null ? d.getStatus().name() : null)
                .requestedAt(d.getRequestedAt()).acceptedAt(d.getAcceptedAt())
                .completedAt(d.getCompletedAt()).rejectedAt(d.getRejectedAt())
                .rejectionReason(d.getRejectionReason()).requestDetails(d.getRequestDetails())
                .responseDetails(d.getResponseDetails()).blockchainTxHash(d.getBlockchainTxHash())
                .notifiedToRequestingDriver(d.isNotifiedToRequestingDriver())
                .notifiedToRespondingDriver(d.isNotifiedToRespondingDriver())
                .build();
    }

    public IncidentInterAgencyCooperation toDomain(IncidentInterAgencyCooperationEntity e) {
        return IncidentInterAgencyCooperation.builder()
                .id(e.getId()).incidentId(e.getIncidentId())
                .requestingAgencyId(e.getRequestingAgencyId()).respondingAgencyId(e.getRespondingAgencyId())
                .cooperationType(e.getCooperationType() != null ? CooperationType.valueOf(e.getCooperationType()) : null)
                .status(e.getStatus() != null ? CooperationStatus.valueOf(e.getStatus()) : null)
                .requestedAt(e.getRequestedAt()).acceptedAt(e.getAcceptedAt())
                .completedAt(e.getCompletedAt()).rejectedAt(e.getRejectedAt())
                .rejectionReason(e.getRejectionReason()).requestDetails(e.getRequestDetails())
                .responseDetails(e.getResponseDetails()).blockchainTxHash(e.getBlockchainTxHash())
                .notifiedToRequestingDriver(e.isNotifiedToRequestingDriver())
                .notifiedToRespondingDriver(e.isNotifiedToRespondingDriver())
                .build();
    }

    public IncidentBlockchainRecordEntity toEntity(IncidentBlockchainRecord d) {
        return IncidentBlockchainRecordEntity.builder()
                .id(d.getId()).incidentId(d.getIncidentId())
                .chainId(d.getChainId()).blockIndex(d.getBlockIndex())
                .previousHash(d.getPreviousHash()).currentHash(d.getCurrentHash())
                .eventType(d.getEventType()).payload(d.getPayload())
                .nonce(d.getNonce()).verified(d.isVerified())
                .build();
    }

    public IncidentBlockchainRecord toDomain(IncidentBlockchainRecordEntity e) {
        return IncidentBlockchainRecord.builder()
                .id(e.getId()).incidentId(e.getIncidentId())
                .chainId(e.getChainId()).blockIndex(e.getBlockIndex())
                .previousHash(e.getPreviousHash()).currentHash(e.getCurrentHash())
                .eventType(e.getEventType()).payload(e.getPayload())
                .createdAt(e.getCreatedAt()).nonce(e.getNonce()).verified(e.isVerified())
                .build();
    }

    public ParcelIncidentLinkEntity toEntity(ParcelIncidentLink d) {
        return ParcelIncidentLinkEntity.builder()
                .parcelId(d.getParcelId()).incidentId(d.getIncidentId())
                .parcelChainId(d.getParcelChainId()).parcelChainTailHash(d.getParcelChainTailHash())
                .incidentChainId(d.getIncidentChainId()).incidentChainHeadHash(d.getIncidentChainHeadHash())
                .incidentChainTailHash(d.getIncidentChainTailHash()).linkedAt(d.getLinkedAt())
                .resumedAt(d.getResumedAt()).resumptionConfirmed(d.isResumptionConfirmed())
                .build();
    }

    public ParcelIncidentLink toDomain(ParcelIncidentLinkEntity e) {
        return ParcelIncidentLink.builder()
                .parcelId(e.getParcelId()).incidentId(e.getIncidentId())
                .parcelChainId(e.getParcelChainId()).parcelChainTailHash(e.getParcelChainTailHash())
                .incidentChainId(e.getIncidentChainId()).incidentChainHeadHash(e.getIncidentChainHeadHash())
                .incidentChainTailHash(e.getIncidentChainTailHash()).linkedAt(e.getLinkedAt())
                .resumedAt(e.getResumedAt()).resumptionConfirmed(e.isResumptionConfirmed())
                .build();
    }

    private String serializeUUIDs(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        try { return objectMapper.writeValueAsString(ids); }
        catch (Exception e) { return "[]"; }
    }

    private List<UUID> deserializeUUIDs(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }
}
