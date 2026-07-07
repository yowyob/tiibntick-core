package com.yowyob.tiibntick.core.incident.adapter.web.mapper;

import com.yowyob.tiibntick.core.incident.adapter.web.dto.IncidentResponse;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
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
                .build();
    }
}
