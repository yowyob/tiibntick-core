package com.yowyob.tiibntick.core.agency.compliance.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Incident reporting via platform tnt-incident-core.
 */
public interface IncidentCorePort {

    Mono<IncidentView> reportIncident(ReportIncidentRequest request);

    Flux<IncidentSummary> listIncidents(ListIncidentsRequest request);

    Mono<IncidentDetail> getIncident(UUID incidentId);

    record ReportIncidentRequest(
            UUID tenantId,
            UUID agencyId,
            UUID missionId,
            String incidentType,
            String description,
            UUID reportedByActorId
    ) {}

    record ListIncidentsRequest(
            UUID tenantId,
            UUID agencyId,
            String status,
            int page,
            int size
    ) {}

    record IncidentView(UUID id, String status, String reference) {}

    record IncidentSummary(
            UUID id,
            String referenceCode,
            UUID missionId,
            String type,
            String status,
            String description,
            Instant reportedAt
    ) {}

    record IncidentDetail(
            UUID id,
            String referenceCode,
            UUID missionId,
            String type,
            String status,
            String severity,
            String description,
            Instant reportedAt,
            Instant resolvedAt,
            boolean slaBreached
    ) {}
}
