package com.yowyob.tiibntick.core.agency.compliance.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.compliance.application.port.out.DisputeCorePort;
import com.yowyob.tiibntick.core.agency.compliance.application.port.out.IncidentCorePort;
import com.yowyob.tiibntick.core.agency.compliance.application.service.ClaimSubmissionService;
import com.yowyob.tiibntick.core.agency.compliance.application.service.ComplianceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Agency ERP compliance endpoints: client disputes (litiges) and delivery incidents.
 */
@Tag(name = "Agency ERP Compliance", description = "Client disputes and delivery incidents")
@RestController
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceQueryService queryService;
    private final ClaimSubmissionService claimSubmissionService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/disputes")
    @Operation(summary = "List client disputes for an agency")
    public Mono<ApiResponse<DisputePageResponse>> listDisputes(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.listDisputes(agencyId, tenantId, status, page, size)
                .map(pageResult -> new DisputePageResponse(
                        pageResult.items().stream().map(ComplianceController::toDisputeItem).toList(),
                        pageResult.page(), pageResult.size(), pageResult.total()))
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/disputes/{disputeId}")
    @Operation(summary = "Get dispute detail")
    public Mono<ApiResponse<DisputeDetailResponse>> getDispute(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @PathVariable String disputeId) {
        return queryService.getDispute(tenantId, disputeId)
                .map(d -> new DisputeDetailResponse(
                        d.id(), d.reference(), d.status(), d.category(), d.priority(),
                        d.missionId(), d.trackingCode(), d.description(), d.claimantId(),
                        d.assignedMediatorId(), d.filedAt(), d.deadline(), d.evidenceCount()))
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/incidents")
    @Operation(summary = "List delivery incidents for an agency")
    public Mono<ApiResponse<List<IncidentResponse>>> listIncidents(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.listIncidents(agencyId, tenantId, status, page, size)
                .map(ComplianceController::toIncident)
                .collectList()
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/incidents/{incidentId}")
    @Operation(summary = "Get incident detail")
    public Mono<ApiResponse<IncidentDetailResponse>> getIncident(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @PathVariable UUID incidentId) {
        return queryService.getIncident(incidentId)
                .map(i -> new IncidentDetailResponse(
                        i.id(), i.referenceCode(), i.missionId(), i.type(), i.status(),
                        i.severity(), i.description(), i.reportedAt(), i.resolvedAt(), i.slaBreached()))
                .map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/claims")
    @Operation(summary = "Submit a client claim (opens a dispute + emits a claim event)")
    public Mono<ApiResponse<ClaimResponse>> submitClaim(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody ClaimRequest body) {
        return claimSubmissionService.submit(
                        tenantId, agencyId, body.missionId(),
                        body.claimType(), body.description(), body.contactEmail())
                .map(r -> new ClaimResponse(r.reference(), r.message()))
                .map(ApiResponse::success);
    }

    private static DisputeItemResponse toDisputeItem(DisputeCorePort.DisputeSummary d) {
        return new DisputeItemResponse(
                d.id(), d.reference(), d.status(), d.category(), d.priority(),
                d.missionId(), d.trackingCode(), d.description(), d.filedAt());
    }

    private static IncidentResponse toIncident(IncidentCorePort.IncidentSummary i) {
        return new IncidentResponse(
                i.id(), i.referenceCode(), i.missionId(), i.type(), i.status(),
                i.description(), i.reportedAt());
    }

    public record ClaimRequest(UUID missionId, String claimType, String description, String contactEmail) {}

    public record ClaimResponse(String reference, String message) {}

    public record DisputePageResponse(
            List<DisputeItemResponse> items, int page, int size, long total) {}

    public record DisputeItemResponse(
            String id, String reference, String status, String category, String priority,
            String missionId, String trackingCode, String description, LocalDateTime filedAt) {}

    public record DisputeDetailResponse(
            String id, String reference, String status, String category, String priority,
            String missionId, String trackingCode, String description, String claimantId,
            String assignedMediatorId, LocalDateTime filedAt, LocalDateTime deadline, int evidenceCount) {}

    public record IncidentResponse(
            UUID id, String referenceCode, UUID missionId, String type, String status,
            String description, Instant reportedAt) {}

    public record IncidentDetailResponse(
            UUID id, String referenceCode, UUID missionId, String type, String status,
            String severity, String description, Instant reportedAt, Instant resolvedAt, boolean slaBreached) {}
}
