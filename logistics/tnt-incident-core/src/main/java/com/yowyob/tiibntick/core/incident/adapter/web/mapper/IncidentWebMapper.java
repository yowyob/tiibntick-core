package com.yowyob.tiibntick.core.incident.adapter.web.mapper;

import com.yowyob.tiibntick.core.incident.adapter.web.dto.AgencyIncidentKpiResponse;
import com.yowyob.tiibntick.core.incident.adapter.web.dto.IncidentBlockchainRecordResponse;
import com.yowyob.tiibntick.core.incident.adapter.web.dto.IncidentResponse;
import com.yowyob.tiibntick.core.incident.adapter.web.dto.IncidentTimelineEntryResponse;
import com.yowyob.tiibntick.core.incident.application.query.AgencyIncidentKpi;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentBlockchainRecord;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import org.springframework.stereotype.Component;

/**
 * Maps Incident domain objects to REST response DTOs.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Component
public class IncidentWebMapper {

    public IncidentResponse toResponse(Incident d) {
        return IncidentResponse.builder()
                .id(d.getId())
                .referenceCode(d.getReferenceCode())
                .tenantId(d.getTenantId())
                .agencyId(d.getAgencyId())
                .missionId(d.getMissionId())
                .platform(d.getSourcePlatform())
                .category(d.getCategory())
                .type(d.getType())
                .severity(d.getSeverity())
                .status(d.getStatus())
                .resolutionMode(d.getResolutionMode())
                .description(d.getDescription())
                .affectedParcelIds(d.getAffectedParcelIds())
                .multiParcelIncident(d.isMultiParcelIncident())
                .ownBlockchainChainId(d.getOwnBlockchainChainId())
                .reportedAt(d.getReportedAt())
                .resolvedAt(d.getResolvedAt())
                .closedAt(d.getClosedAt())
                .lastEscalationLevel(d.getLastEscalationLevel())
                .autoResolutionAttempts(d.getAutoResolutionAttempts())
                .interAgencyInvolved(d.isInterAgencyInvolved())
                .riskScore(d.getRiskScore() != null ? d.getRiskScore().getGlobalScore() : null)
                .slaBreached(d.getSlaImpact() != null && d.getSlaImpact().isSlaBreached())
                .reportedByActorId(d.getReportedByActorId())
                .responsibleOrgId(d.getResponsibleOrgId())
                .responsibleOrgType(d.getResponsibleOrgType())
                .trackingCode(d.getTrackingCode())
                .build();
    }

    public IncidentTimelineEntryResponse toTimelineResponse(IncidentEventLog e) {
        return IncidentTimelineEntryResponse.builder()
                .id(e.getId())
                .incidentId(e.getIncidentId())
                .eventType(e.getEventType())
                .occurredAt(e.getOccurredAt())
                .performedByActorId(e.getPerformedByActorId())
                .performedByRole(e.getPerformedByRole())
                .payload(e.getPayload())
                .blockchainTxHash(e.getBlockchainTxHash())
                .blockchainChainRef(e.getBlockchainChainRef())
                .writtenOnParcelChain(e.isWrittenOnParcelChain())
                .writtenOnIncidentChain(e.isWrittenOnIncidentChain())
                .build();
    }

    public IncidentBlockchainRecordResponse toBlockchainResponse(IncidentBlockchainRecord r) {
        return IncidentBlockchainRecordResponse.builder()
                .id(r.getId())
                .incidentId(r.getIncidentId())
                .chainId(r.getChainId())
                .blockIndex(r.getBlockIndex())
                .previousHash(r.getPreviousHash())
                .currentHash(r.getCurrentHash())
                .eventType(r.getEventType())
                .payload(r.getPayload())
                .createdAt(r.getCreatedAt())
                .nonce(r.getNonce())
                .verified(r.isVerified())
                .build();
    }

    public AgencyIncidentKpiResponse toKpiResponse(AgencyIncidentKpi k) {
        return AgencyIncidentKpiResponse.builder()
                .agencyId(k.getAgencyId())
                .totalActive(k.getTotalActive())
                .totalResolved(k.getTotalResolved())
                .totalEscalated(k.getTotalEscalated())
                .totalInterAgency(k.getTotalInterAgency())
                .slaBreaches(k.getSlaBreaches())
                .avgResolutionMinutes(k.getAvgResolutionMinutes())
                .last24hIncidents(k.getLast24hIncidents())
                .build();
    }
}
