package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.dispute.application.command.StartMediationCommand;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request.DisputeRequests;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.response.DisputeResponses;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.mapper.DisputeRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for mediation and arbitration actions on disputes.
 *
 * <p>Exposes mediator-scoped operations: start mediation, issue a ruling,
 * escalate to arbitration. These endpoints are restricted to authenticated
 * mediators and platform administrators.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/disputes/{disputeId}/mediation")
@Tag(name = "Mediation & Arbitration", description = "Mediator actions: start mediation, issue ruling, escalate to arbitration")
public class MediationController {

    private final IDisputeCommandUseCase commandUseCase;

    public MediationController(IDisputeCommandUseCase commandUseCase) {
        this.commandUseCase = commandUseCase;
    }

    // =========================================================================
    // START MEDIATION — POST /api/v1/disputes/{disputeId}/mediation/start
    // =========================================================================

    @PostMapping("/start")
    @Operation(summary = "Start the formal mediation phase",
            description = "Transitions the dispute from UNDER_INVESTIGATION to MEDIATION_IN_PROGRESS. "
                    + "Requires an assigned mediator.")
    public Mono<DisputeResponses.DisputeDetailResponse> startMediation(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Actor-ID") String mediatorId,
            @PathVariable String disputeId) {
        return commandUseCase.startMediation(
                        new StartMediationCommand(DisputeId.of(disputeId), tenantId, mediatorId))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // RULE — POST /api/v1/disputes/{disputeId}/mediation/rule
    // =========================================================================

    @PostMapping("/rule")
    @Operation(summary = "Issue a ruling on the dispute",
            description = "Records the mediator's decision. If compensation is required, "
                    + "the dispute transitions to PENDING_COMPENSATION and triggers tnt-billing-wallet.")
    public Mono<DisputeResponses.DisputeDetailResponse> rule(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Actor-ID") String ruledBy,
            @PathVariable String disputeId,
            @RequestBody DisputeRequests.RuleDisputeRequest request) {
        return commandUseCase.ruleDispute(DisputeRestMapper.toCommand(request, disputeId, tenantId, ruledBy))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // ESCALATE — POST /api/v1/disputes/{disputeId}/mediation/escalate
    // =========================================================================

    @PostMapping("/escalate")
    @Operation(summary = "Escalate the dispute to arbitration",
            description = "Used for complex cases, fraud suspicion, or when mediation fails. "
                    + "Moves to PENDING_ARBITRATION and notifies the assigned arbitrator.")
    public Mono<DisputeResponses.DisputeDetailResponse> escalate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String disputeId,
            @RequestBody DisputeRequests.EscalateDisputeRequest request) {
        return commandUseCase.escalateDispute(DisputeRestMapper.toCommand(request, disputeId, tenantId))
                .map(DisputeRestMapper::toDetailResponse);
    }
}
