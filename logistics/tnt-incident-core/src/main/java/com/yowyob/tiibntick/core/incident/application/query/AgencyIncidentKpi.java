package com.yowyob.tiibntick.core.incident.application.query;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * KPI snapshot aggregating incident counts, resolution time and SLA breaches for an agency.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class AgencyIncidentKpi {
    UUID agencyId;
    long totalActive;
    long totalResolved;
    long totalEscalated;
    long totalInterAgency;
    long slaBreaches;
    double avgResolutionMinutes;
    long last24hIncidents;
}
