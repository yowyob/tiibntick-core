package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
/**
 * Response DTO representing an incident summary.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class IncidentResponse {
    UUID id;
    String referenceCode;
    UUID tenantId;
    UUID agencyId;
    UUID missionId;
    PlatformType platform;
    IncidentCategory category;
    IncidentType type;
    IncidentSeverity severity;
    IncidentStatus status;
    ResolutionMode resolutionMode;
    String description;
    List<UUID> affectedParcelIds;
    boolean multiParcelIncident;
    String ownBlockchainChainId;
    Instant reportedAt;
    Instant resolvedAt;
    Instant closedAt;
    int lastEscalationLevel;
    int autoResolutionAttempts;
    boolean interAgencyInvolved;
    Double riskScore;
    boolean slaBreached;
    UUID reportedByActorId;
    String responsibleOrgId;
    String responsibleOrgType;
    String trackingCode;
    /** Lot L3: blocks the delivery (QO-2). */
    boolean blocking;
    /** Lot L3: what the resolution does (or proposes) to the delivery. */
    com.yowyob.tiibntick.core.incident.domain.enums.IncidentDeliveryOutcome deliveryOutcome;
    com.yowyob.tiibntick.core.incident.domain.enums.IncidentResponsibleParty responsibleParty;
    String resolutionNote;
    /** Lot L3 (QO-14): when the resolution was proposed; confirmed automatically after the configured delay. */
    Instant resolutionProposedAt;
}
