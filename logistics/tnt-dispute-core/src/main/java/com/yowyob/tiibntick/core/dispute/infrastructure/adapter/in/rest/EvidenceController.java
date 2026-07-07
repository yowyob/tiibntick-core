package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.dispute.application.port.inbound.IEvidenceUseCase;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request.DisputeRequests;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.response.DisputeResponses;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.mapper.DisputeRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for evidence management on disputes.
 *
 * <p>Allows parties to submit evidence (photos, GPS traces, blockchain proofs),
 * and mediators to verify evidence records. Integrates with tnt-trust for
 * blockchain anchoring when required.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/disputes/{disputeId}/evidences")
@Tag(name = "Evidence Management", description = "Submit, list, and verify evidence on a dispute")
public class EvidenceController {

    private final IEvidenceUseCase evidenceUseCase;

    public EvidenceController(IEvidenceUseCase evidenceUseCase) {
        this.evidenceUseCase = evidenceUseCase;
    }

    // =========================================================================
    // SUBMIT EVIDENCE — POST /api/v1/disputes/{disputeId}/evidences
    // =========================================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit evidence to a dispute",
            description = "Supports PHOTO, VIDEO, DOCUMENT, GPS_TRACE, BLOCKCHAIN_PROOF, SIGNATURE_RECORD evidence types. "
                    + "Blockchain-eligible types are automatically anchored via tnt-trust.")
    public Mono<DisputeResponses.DisputeDetailResponse> submitEvidence(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String disputeId,
            @RequestBody DisputeRequests.AddEvidenceRequest request) {
        return evidenceUseCase.submitEvidence(DisputeRestMapper.toCommand(request, disputeId, tenantId))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // LIST EVIDENCES — GET /api/v1/disputes/{disputeId}/evidences
    // =========================================================================

    @GetMapping
    @Operation(summary = "List all evidence records for a dispute")
    public Flux<DisputeResponses.EvidenceResponse> getEvidences(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String disputeId) {
        return evidenceUseCase.getEvidenceForDispute(DisputeId.of(disputeId), tenantId)
                .map(DisputeRestMapper::toEvidenceResponse);
    }

    // =========================================================================
    // REQUEST EVIDENCE — POST /api/v1/disputes/{disputeId}/evidences/request
    // =========================================================================

    @PostMapping("/request")
    @Operation(summary = "Request additional evidence from a party",
            description = "Moves the dispute to AWAITING_EVIDENCE status and notifies the specified party.")
    public Mono<DisputeResponses.DisputeDetailResponse> requestEvidence(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Actor-ID") String actorId,
            @PathVariable String disputeId,
            @RequestBody DisputeRequests.RequestEvidenceRequest request) {
        return evidenceUseCase.requestEvidence(DisputeRestMapper.toCommand(request, disputeId, tenantId, actorId))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // VERIFY EVIDENCE — PUT /api/v1/disputes/{disputeId}/evidences/{evidenceId}/verify
    // =========================================================================

    @PutMapping("/{evidenceId}/verify")
    @Operation(summary = "Verify a piece of evidence (mediator action)",
            description = "Marks evidence as mediator-verified. Triggers blockchain anchoring if not already done.")
    public Mono<DisputeResponses.EvidenceResponse> verifyEvidence(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String disputeId,
            @PathVariable String evidenceId,
            @RequestBody DisputeRequests.VerifyEvidenceRequest request) {
        return evidenceUseCase.verifyEvidence(DisputeId.of(disputeId), evidenceId, request.mediatorId(), tenantId)
                .map(DisputeRestMapper::toEvidenceResponse);
    }
}
