package com.yowyob.tiibntick.core.incident.adapter.web;

import com.yowyob.tiibntick.core.incident.adapter.web.dto.*;
import com.yowyob.tiibntick.core.incident.adapter.web.mapper.IncidentWebMapper;
import com.yowyob.tiibntick.core.incident.application.command.*;
import com.yowyob.tiibntick.core.incident.application.query.ListIncidentsQuery;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.port.inbound.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller exposing the main incident management API under /api/v1/incidents.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IReportIncidentUseCase reportIncidentUseCase;
    private final IReportDriverWithdrawalUseCase reportDriverWithdrawalUseCase;
    private final ITriageIncidentUseCase triageIncidentUseCase;
    private final IStartAutoResolutionUseCase startAutoResolutionUseCase;
    private final IResolveIncidentUseCase resolveIncidentUseCase;
    private final ICloseIncidentUseCase closeIncidentUseCase;
    private final ICancelIncidentUseCase cancelIncidentUseCase;
    private final IEscalateIncidentUseCase escalateIncidentUseCase;
    private final IAttachEvidenceUseCase attachEvidenceUseCase;
    private final IQueryIncidentUseCase queryIncidentUseCase;
    private final IncidentWebMapper webMapper;

    /**
     * Reports a new delivery incident.
     *
     * @param req the incident report payload
     * @return HTTP 201 with the created incident
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<IncidentResponse> reportIncident(@Valid @RequestBody ReportIncidentRequest req) {
        return reportIncidentUseCase.execute(
                ReportIncidentCommand.builder()
                        .tenantId(req.getTenantId()).agencyId(req.getAgencyId())
                        .missionId(req.getMissionId()).platform(req.getPlatform())
                        .type(req.getType()).description(req.getDescription())
                        .reportedByActorId(req.getReportedByActorId())
                        .reportedByRole(req.getReportedByRole())
                        .affectedParcelIds(req.getAffectedParcelIds())
                        .currentLat(req.getCurrentLat()).currentLng(req.getCurrentLng())
                        .build()
        ).map(webMapper::toResponse);
    }

    /**
     * Reports a driver withdrawal as an incident.
     *
     * @param req the withdrawal payload
     * @return HTTP 201 with the created incident
     */
    @PostMapping("/driver-withdrawal")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<IncidentResponse> reportWithdrawal(@Valid @RequestBody ReportIncidentRequest req) {
        return reportDriverWithdrawalUseCase.execute(
                ReportDriverWithdrawalCommand.builder()
                        .tenantId(req.getTenantId()).agencyId(req.getAgencyId())
                        .missionId(req.getMissionId()).platform(req.getPlatform())
                        .driverActorId(req.getReportedByActorId())
                        .driverRole(req.getReportedByRole())
                        .withdrawalType(req.getType()).justification(req.getDescription())
                        .affectedParcelIds(req.getAffectedParcelIds())
                        .currentLat(req.getCurrentLat()).currentLng(req.getCurrentLng())
                        .build()
        ).map(webMapper::toResponse);
    }

    /**
     * Retrieves an incident by its UUID.
     *
     * @param id the incident UUID
     * @return the incident response
     */
    @GetMapping("/{id}")
    public Mono<IncidentResponse> getById(@PathVariable UUID id) {
        return queryIncidentUseCase.getById(id).map(webMapper::toResponse);
    }

    /**
     * Retrieves an incident by its human-readable reference code.
     *
     * @param referenceCode the reference code (e.g. TNT-INC-042381)
     * @return the incident response
     */
    @GetMapping("/ref/{referenceCode}")
    public Mono<IncidentResponse> getByRef(@PathVariable String referenceCode) {
        return queryIncidentUseCase.getByReferenceCode(referenceCode).map(webMapper::toResponse);
    }

    /**
     * Lists incidents for an agency with optional status filter.
     *
     * @param agencyId the agency UUID
     * @param tenantId the tenant UUID
     * @param status   optional status filter
     * @param page     zero-based page index
     * @param size     page size
     * @return stream of incident responses
     */
    @GetMapping
    public Flux<IncidentResponse> listByAgency(
            @RequestParam UUID agencyId,
            @RequestParam UUID tenantId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryIncidentUseCase.listByAgency(
                ListIncidentsQuery.builder()
                        .agencyId(agencyId).tenantId(tenantId)
                        .status(status).page(page).size(size)
                        .build()
        ).map(webMapper::toResponse);
    }

    /**
     * Triggers the triage phase for an incident.
     *
     * @param id  the incident UUID
     * @param req triage parameters
     * @return the triaged incident
     */
    @PostMapping("/{id}/triage")
    public Mono<IncidentResponse> triage(@PathVariable UUID id,
                                          @Valid @RequestBody TriageRequest req) {
        return triageIncidentUseCase.execute(
                TriageIncidentCommand.builder()
                        .incidentId(id)
                        .triggeredByActorId(req.getTriggeredByActorId())
                        .driverReputationScore(req.getDriverReputationScore())
                        .parcelValueNormalized(req.getParcelValueNormalized())
                        .zoneDangerIndex(req.getZoneDangerIndex())
                        .cargoSensitivity(req.getCargoSensitivity())
                        .weatherIndex(req.getWeatherIndex())
                        .driverIncidentHistory(req.getDriverIncidentHistory())
                        .missionComplexity(req.getMissionComplexity())
                        .slaDeadlineEpochSeconds(req.getSlaDeadlineEpochSeconds())
                        .build()
        ).map(webMapper::toResponse);
    }

    /**
     * Starts the automatic resolution engine for an incident.
     *
     * @param id the incident UUID
     * @return the incident in AUTO_RESOLVING state
     */
    @PostMapping("/{id}/auto-resolve")
    public Mono<IncidentResponse> startAutoResolution(@PathVariable UUID id) {
        return startAutoResolutionUseCase.execute(id).map(webMapper::toResponse);
    }

    /**
     * Escalates an incident.
     *
     * @param id  the incident UUID
     * @param req escalation parameters
     * @return the escalated incident
     */
    @PostMapping("/{id}/escalate")
    public Mono<IncidentResponse> escalate(@PathVariable UUID id,
                                            @Valid @RequestBody EscalateRequest req) {
        return escalateIncidentUseCase.execute(
                EscalateIncidentCommand.builder()
                        .incidentId(id)
                        .escalatedByActorId(req.getEscalatedByActorId())
                        .escalatedByRole(req.getEscalatedByRole())
                        .targetActorId(req.getTargetActorId())
                        .targetRole(req.getTargetRole())
                        .reason(req.getReason())
                        .triggerDispute(req.isTriggerDispute())
                        .fraudEvidence(req.getFraudEvidence())
                        .build()
        ).map(webMapper::toResponse);
    }

    /**
     * Marks an incident as resolved.
     *
     * @param id  the incident UUID
     * @param req resolution parameters
     * @return the resolved incident
     */
    @PostMapping("/{id}/resolve")
    public Mono<IncidentResponse> resolve(@PathVariable UUID id,
                                           @Valid @RequestBody ResolveRequest req) {
        return resolveIncidentUseCase.execute(
                ResolveIncidentCommand.builder()
                        .incidentId(id)
                        .resolvedByActorId(req.getResolvedByActorId())
                        .resolutionMode(req.getResolutionMode())
                        .resolutionNotes(req.getResolutionNotes())
                        .build()
        ).map(webMapper::toResponse);
    }

    /**
     * Closes a resolved incident.
     *
     * @param id              the incident UUID
     * @param closedByActorId the actor performing the closure
     * @return the closed incident
     */
    @PostMapping("/{id}/close")
    public Mono<IncidentResponse> close(@PathVariable UUID id,
                                         @RequestParam UUID closedByActorId) {
        return closeIncidentUseCase.execute(id, closedByActorId).map(webMapper::toResponse);
    }

    /**
     * Cancels an incident.
     *
     * @param id                 the incident UUID
     * @param cancelledByActorId the actor performing the cancellation
     * @param reason             cancellation reason
     * @return the cancelled incident
     */
    @PostMapping("/{id}/cancel")
    public Mono<IncidentResponse> cancel(@PathVariable UUID id,
                                          @RequestParam UUID cancelledByActorId,
                                          @RequestParam String reason) {
        return cancelIncidentUseCase.execute(id, cancelledByActorId, reason).map(webMapper::toResponse);
    }

    /**
     * Attaches a digital evidence file to an incident.
     *
     * @param id  the incident UUID
     * @param req evidence details
     */
    @PostMapping("/{id}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> attachEvidence(@PathVariable UUID id,
                                      @Valid @RequestBody AttachEvidenceRequest req) {
        return attachEvidenceUseCase.execute(
                AttachEvidenceCommand.builder()
                        .incidentId(id)
                        .evidenceType(req.getEvidenceType())
                        .fileUrl(req.getFileUrl())
                        .mimeType(req.getMimeType())
                        .capturedByActorId(req.getCapturedByActorId())
                        .capturedByRole(req.getCapturedByRole())
                        .sha256Checksum(req.getSha256Checksum())
                        .latitude(req.getLatitude())
                        .longitude(req.getLongitude())
                        .build()
        ).then();
    }

    /**
     * Returns the ordered event timeline for an incident.
     *
     * @param id the incident UUID
     * @return ordered stream of event log entries
     */
    @GetMapping("/{id}/timeline")
    public Flux<?> getTimeline(@PathVariable UUID id) {
        return queryIncidentUseCase.getTimeline(id);
    }

    /**
     * Returns the blockchain chain blocks for a multi-parcel incident.
     *
     * @param id the incident UUID
     * @return ordered stream of blockchain records
     */
    @GetMapping("/{id}/blockchain")
    public Flux<?> getBlockchainChain(@PathVariable UUID id) {
        return queryIncidentUseCase.getBlockchainChain(id);
    }

    /**
     * Returns incident KPI metrics for an agency.
     *
     * @param agencyId the agency UUID
     * @param tenantId the tenant UUID
     * @return the KPI snapshot
     */
    @GetMapping("/kpi")
    public Mono<?> getKpi(@RequestParam UUID agencyId, @RequestParam UUID tenantId) {
        return queryIncidentUseCase.getAgencyKpi(agencyId, tenantId);
    }
}
