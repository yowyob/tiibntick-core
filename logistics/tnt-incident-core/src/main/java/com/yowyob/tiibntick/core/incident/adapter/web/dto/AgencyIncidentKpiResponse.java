package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import lombok.*;
import java.util.UUID;
/**
 * Response DTO for the incident KPI snapshot of an agency.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AgencyIncidentKpiResponse {
    UUID agencyId;
    long totalActive;
    long totalResolved;
    long totalEscalated;
    long totalInterAgency;
    long slaBreaches;
    double avgResolutionMinutes;
    long last24hIncidents;
}
