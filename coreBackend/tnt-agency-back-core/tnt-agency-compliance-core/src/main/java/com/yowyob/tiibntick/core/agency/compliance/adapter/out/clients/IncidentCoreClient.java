package com.yowyob.tiibntick.core.agency.compliance.adapter.out.clients;

import com.yowyob.tiibntick.core.agency.compliance.application.port.out.IncidentCorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter to platform tnt-incident-core. Ported from the BFF (now lives in the ERP).
 */
@Component
public class IncidentCoreClient implements IncidentCorePort {

    private static final Logger log = LoggerFactory.getLogger(IncidentCoreClient.class);

    private final WebClient webClient;

    public IncidentCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<IncidentView> reportIncident(ReportIncidentRequest request) {
        Map<String, Object> body = Map.of(
                "tenantId", request.tenantId().toString(),
                "agencyId", request.agencyId().toString(),
                "missionId", request.missionId().toString(),
                "platform", "AGENCY",
                "type", mapIncidentType(request.incidentType()),
                "description", request.description(),
                "reportedByActorId", request.reportedByActorId().toString(),
                "reportedByRole", "DELIVERER"
        );
        return webClient.post()
                .uri("/api/v1/incidents")
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CoreIncidentResponse.class)
                .map(r -> new IncidentView(r.id(), r.status(), r.referenceCode()))
                .doOnError(e -> log.warn("[IncidentCore] report failed mission={}: {}",
                        request.missionId(), e.getMessage()));
    }

    @Override
    public Flux<IncidentSummary> listIncidents(ListIncidentsRequest request) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/v1/incidents")
                            .queryParam("agencyId", request.agencyId().toString())
                            .queryParam("tenantId", request.tenantId().toString())
                            .queryParam("page", request.page())
                            .queryParam("size", request.size());
                    if (request.status() != null && !request.status().isBlank()) {
                        builder.queryParam("status", request.status());
                    }
                    return builder.build();
                })
                .header("X-Tenant-Id", request.tenantId().toString())
                .retrieve()
                .bodyToFlux(CoreIncidentListItem.class)
                .map(item -> new IncidentSummary(
                        item.id(),
                        item.referenceCode(),
                        item.missionId(),
                        item.type() != null ? item.type().toString() : null,
                        item.status() != null ? item.status().toString() : null,
                        item.description(),
                        item.reportedAt()))
                .doOnError(e -> log.warn("[IncidentCore] list failed agency={}: {}",
                        request.agencyId(), e.getMessage()));
    }

    @Override
    public Mono<IncidentDetail> getIncident(UUID incidentId) {
        return webClient.get()
                .uri("/api/v1/incidents/{id}", incidentId)
                .retrieve()
                .bodyToMono(CoreIncidentDetail.class)
                .map(item -> new IncidentDetail(
                        item.id(),
                        item.referenceCode(),
                        item.missionId(),
                        item.type() != null ? item.type().toString() : null,
                        item.status() != null ? item.status().toString() : null,
                        item.severity() != null ? item.severity().toString() : null,
                        item.description(),
                        item.reportedAt(),
                        item.resolvedAt(),
                        item.slaBreached()))
                .doOnError(e -> log.warn("[IncidentCore] get failed id={}: {}", incidentId, e.getMessage()));
    }

    private static String mapIncidentType(String anomalyType) {
        if (anomalyType == null) {
            return "AGENCY_SYSTEM_DOWN";
        }
        return switch (anomalyType.toUpperCase()) {
            case "GPS_LOSS", "GPS" -> "DRIVER_GPS_LOSS";
            case "THEFT" -> "DRIVER_PARCEL_THEFT_ATTEMPT";
            case "ACCIDENT" -> "DRIVER_ACCIDENT_PHYSICAL";
            case "CLIENT_REFUSAL" -> "CLIENT_REFUSED_RECEPTION";
            case "DAMAGE" -> "PARCEL_PHYSICALLY_DAMAGED";
            case "DELAY" -> "SLA_BREACH_TRAFFIC_DELAY";
            default -> "AGENCY_SYSTEM_DOWN";
        };
    }

    private record CoreIncidentResponse(UUID id, String status, String referenceCode) {}

    private record CoreIncidentListItem(
            UUID id,
            String referenceCode,
            UUID missionId,
            Object type,
            Object status,
            String description,
            Instant reportedAt) {}

    private record CoreIncidentDetail(
            UUID id,
            String referenceCode,
            UUID missionId,
            Object type,
            Object status,
            Object severity,
            String description,
            Instant reportedAt,
            Instant resolvedAt,
            boolean slaBreached) {}
}
