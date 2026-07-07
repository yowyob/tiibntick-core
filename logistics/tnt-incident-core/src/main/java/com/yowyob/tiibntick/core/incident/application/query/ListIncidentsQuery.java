package com.yowyob.tiibntick.core.incident.application.query;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import lombok.Builder;
import lombok.Value;
import java.time.Instant;
import java.util.UUID;
/**
 * Query object for paginated and filtered incident listing by agency.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class ListIncidentsQuery {
    UUID tenantId;
    UUID agencyId;
    PlatformType platform;
    IncidentStatus status;
    IncidentCategory category;
    IncidentSeverity minSeverity;
    Instant from;
    Instant to;
    int page;
    int size;
}
