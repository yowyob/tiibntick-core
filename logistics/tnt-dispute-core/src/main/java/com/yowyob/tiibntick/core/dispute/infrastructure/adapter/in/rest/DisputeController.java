package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeAgainstFreelancerOrgCommand;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.application.query.GetDisputeQuery;
import com.yowyob.tiibntick.core.dispute.application.query.ListDisputesQuery;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeCategory;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputePriority;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request.DisputeRequests;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.response.DisputeResponses;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.mapper.DisputeRestMapper;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * REST controller for dispute management operations.
 *
 * <p>Exposes the dispute lifecycle API consumed by all TiiBnTick platforms
 * (Agency, Go, Link, Point, Freelancer, Market) via the tnt-bootstrap BFF, and called
 * server-to-server by {@code coreBackend/tnt-agency-back-core} (Kafka-driven flows with
 * no end-user session to forward a JWT from).
 *
 * <p>Tenant isolation is enforced via {@code @CurrentUser TntUserIdentity} (Audit n°7 · #4
 * remediation, 2026-07-18) — never the client-supplied {@code X-Tenant-ID} header, which
 * any caller could previously forge to read/write another tenant's disputes. The identity
 * is resolved from either an end-user JWT or, for the internal agency-back-core caller,
 * a platform Client-Id/Api-Key call scoped to {@code DISPUTE:*} (see
 * {@code tnt-platform-gateway-core}'s {@code TntPlatformGatewaySecurityConfig}
 * {@code @Order(11)} chain) — {@code TntUserIdentity} carries the correctly-resolved
 * tenant either way, so this controller needs no branching logic of its own.
 *
 * <p>Role/ownership enforcement: {@code @RequirePermission(resource = "dispute", ...)}
 * on the application service layer (Go-Freelancer integration hardening) replaces the
 * former class-wide {@code @PreAuthorize("isAuthenticated()")}, which only rejected
 * anonymous callers and left every authenticated role (including a bare freelancer JWT)
 * able to call mediator-tier actions on any tenant's dispute. Non-privileged callers
 * (no {@code dispute:resolve}) are additionally restricted to disputes where they are
 * the claimant or respondent, enforced in {@code DisputeQueryService}/{@code DisputeCommandService}.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/disputes")
@Tag(name = "Dispute Management", description = "Full dispute lifecycle: open, investigate, mediate, rule, compensate, close")
public class DisputeController {

    private final IDisputeCommandUseCase commandUseCase;
    private final IDisputeQueryUseCase queryUseCase;

    public DisputeController(IDisputeCommandUseCase commandUseCase, IDisputeQueryUseCase queryUseCase) {
        this.commandUseCase = commandUseCase;
        this.queryUseCase = queryUseCase;
    }

    private boolean isPrivileged(TntUserIdentity currentUser) {
        return currentUser.hasPermission("dispute", "resolve");
    }

    /** Non-privileged callers may only act as themselves — reject impersonation attempts. */
    private void assertActingAsSelf(TntUserIdentity currentUser, String actorIdInPayload) {
        String self = currentUser.actorId() != null ? currentUser.actorId().toString() : null;
        if (!isPrivileged(currentUser) && (actorIdInPayload == null || !actorIdInPayload.equals(self))) {
            throw TntRoleException.forbidden("dispute", "create");
        }
    }

    // =========================================================================
    // OPEN — POST /api/v1/disputes
    // =========================================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new dispute",
            description = "Opens a dispute for a logistic incident. Validates no duplicate exists for the same package.")
    public Mono<DisputeResponses.DisputeOpenedResponse> openDispute(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestBody DisputeRequests.OpenDisputeRequest request) {
        assertActingAsSelf(currentUser, request.claimantId());
        String tenantId = currentUser.tenantId().toString();
        return commandUseCase.openDispute(DisputeRestMapper.toCommand(request, tenantId))
                .map(DisputeRestMapper::toOpenedResponse);
    }

    // =========================================================================
    // GET — GET /api/v1/disputes/{id}
    // =========================================================================

    @GetMapping("/{id}")
    @Operation(summary = "Get dispute by ID", description = "Returns full dispute details including evidences, resolution, and SLA status.")
    public Mono<DisputeResponses.DisputeDetailResponse> getDispute(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String id) {
        String tenantId = currentUser.tenantId().toString();
        String requesterId = currentUser.actorId() != null ? currentUser.actorId().toString() : null;
        return queryUseCase.getDispute(new GetDisputeQuery(DisputeId.of(id), tenantId, requesterId, isPrivileged(currentUser)))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // GET by reference — GET /api/v1/disputes/by-reference/{ref}
    // =========================================================================

    @GetMapping("/by-reference/{reference}")
    @Operation(summary = "Get dispute by reference code",
            description = "Finds a dispute using its human-readable reference (e.g. DSP-202601-00042).")
    public Mono<DisputeResponses.DisputeDetailResponse> getByReference(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String reference) {
        String tenantId = currentUser.tenantId().toString();
        String requesterId = currentUser.actorId() != null ? currentUser.actorId().toString() : null;
        return queryUseCase.getByReference(reference, tenantId, requesterId, isPrivileged(currentUser))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // LIST — GET /api/v1/disputes
    // =========================================================================

    @GetMapping
    @Operation(summary = "List disputes with filters and pagination")
    public Mono<DisputeResponses.DisputePageResponse> listDisputes(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String claimantId,
            @RequestParam(required = false) String respondentId,
            @RequestParam(required = false) String missionId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = currentUser.tenantId().toString();
        String requesterId = currentUser.actorId() != null ? currentUser.actorId().toString() : null;

        ListDisputesQuery query = new ListDisputesQuery(
                tenantId,
                requesterId,
                status != null ? DisputeStatus.valueOf(status) : null,
                priority != null ? DisputePriority.valueOf(priority) : null,
                category != null ? DisputeCategory.valueOf(category) : null,
                claimantId,
                respondentId,
                missionId,
                from != null ? LocalDateTime.parse(from) : null,
                to != null ? LocalDateTime.parse(to) : null,
                page,
                size,
                isPrivileged(currentUser));


        return queryUseCase.listDisputes(query)
                .map(result -> new DisputeResponses.DisputePageResponse(
                        result.content().stream().map(DisputeRestMapper::toSummaryResponse).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.hasNextPage(),
                        result.hasPreviousPage()));
    }

    // =========================================================================
    // LIST by claimant — GET /api/v1/disputes/by-claimant/{claimantId}
    // =========================================================================

    @GetMapping("/by-claimant/{claimantId}")
    @Operation(summary = "Get all active disputes for a claimant")
    public Flux<DisputeResponses.DisputeSummaryResponse> getByClaimant(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String claimantId) {
        String tenantId = currentUser.tenantId().toString();
        String requesterId = currentUser.actorId() != null ? currentUser.actorId().toString() : null;
        return queryUseCase.getDisputesByClaimant(claimantId, tenantId, requesterId, isPrivileged(currentUser))
                .map(DisputeRestMapper::toSummaryResponse);
    }

    // =========================================================================
    // ASSIGN MEDIATOR — PUT /api/v1/disputes/{id}/mediator
    // =========================================================================

    @PutMapping("/{id}/mediator")
    @Operation(summary = "Assign a mediator to a dispute")
    public Mono<DisputeResponses.DisputeDetailResponse> assignMediator(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String id,
            @RequestBody DisputeRequests.AssignMediatorRequest request) {
        String tenantId = currentUser.tenantId().toString();
        return commandUseCase.assignMediator(DisputeRestMapper.toCommand(request, id, tenantId))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // ADD COMMENT — POST /api/v1/disputes/{id}/comments
    // =========================================================================

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a comment on the dispute thread")
    public Mono<DisputeResponses.DisputeDetailResponse> addComment(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String id,
            @RequestBody DisputeRequests.AddCommentRequest request) {
        assertActingAsSelf(currentUser, request.authorId());
        String tenantId = currentUser.tenantId().toString();
        return commandUseCase.addComment(DisputeRestMapper.toCommand(request, id, tenantId))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // WITHDRAW — DELETE /api/v1/disputes/{id}
    // =========================================================================

    @DeleteMapping("/{id}")
    @Operation(summary = "Withdraw a dispute (claimant action)")
    public Mono<DisputeResponses.DisputeDetailResponse> withdraw(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String id,
            @RequestBody DisputeRequests.WithdrawDisputeRequest request) {
        assertActingAsSelf(currentUser, request.claimantId());
        String tenantId = currentUser.tenantId().toString();
        return commandUseCase.withdrawDispute(DisputeRestMapper.toCommand(request, id, tenantId))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // CLOSE — POST /api/v1/disputes/{id}/close
    // =========================================================================

    @PostMapping("/{id}/close")
    @Operation(summary = "Administratively close a dispute")
    public Mono<DisputeResponses.DisputeDetailResponse> closeDispute(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String id,
            @RequestBody DisputeRequests.CloseDisputeRequest request) {
        String tenantId = currentUser.tenantId().toString();
        return commandUseCase.closeDispute(DisputeRestMapper.toCommand(request, id, tenantId))
                .map(DisputeRestMapper::toDetailResponse);
    }

    // =========================================================================
    // PROCESS COMPENSATION — POST /api/v1/disputes/{id}/compensation
    // =========================================================================

    @PostMapping("/{id}/compensation")
    @Operation(summary = "Mark compensation as processed after payment confirmation")
    public Mono<DisputeResponses.DisputeDetailResponse> processCompensation(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String id,
            @RequestBody DisputeRequests.ProcessCompensationRequest request) {
        String tenantId = currentUser.tenantId().toString();
        return commandUseCase.processCompensation(DisputeRestMapper.toCommand(request, id, tenantId))
                .map(DisputeRestMapper::toDetailResponse);
    }
    // ── : FreelancerOrg dispute endpoints ─────────────────────────────────

    /**
     * POST /disputes/freelancer-org
     * Opens a dispute specifically against a FreelancerOrganization.
     */
    @PostMapping("/freelancer-org")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a dispute against a FreelancerOrg",
        description = " — Opens a dispute targeting a FreelancerOrganization as the respondent. "
                    + "Optionally tracks the sub-deliverer who executed the disputed delivery.")
    public Mono<DisputeResponses.DisputeDetailResponse> openDisputeAgainstFreelancerOrg(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestBody OpenDisputeAgainstFreelancerOrgCommand cmd) {
        assertActingAsSelf(currentUser, cmd.claimantId());
        String tenantId = currentUser.tenantId().toString();
        OpenDisputeAgainstFreelancerOrgCommand tenantSafeCmd = new OpenDisputeAgainstFreelancerOrgCommand(
                tenantId, cmd.claimantId(), cmd.claimantType(), cmd.freelancerOrgId(), cmd.freelancerOrgOwnerId(),
                cmd.impliedSubDelivererId(), cmd.cause(), cmd.category(), cmd.priority(), cmd.missionId(),
                cmd.packageId(), cmd.trackingCode(), cmd.description());
        return commandUseCase.openAgainstFreelancerOrg(tenantSafeCmd)
                .map(DisputeRestMapper::toDetailResponse);
    }

    /**
     * GET /disputes/freelancer-org/{orgId}?status=...
     * Lists all disputes against a FreelancerOrg.
     */
    @GetMapping("/freelancer-org/{orgId}")
    @Operation(summary = "List disputes against a FreelancerOrg")
    public Flux<DisputeResponses.DisputeSummaryResponse> getDisputesByFreelancerOrg(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String orgId,
            @RequestParam(required = false) String status) {
        String tenantId = currentUser.tenantId().toString();
        return queryUseCase.findDisputesByFreelancerOrg(orgId, status, tenantId)
                .map(DisputeRestMapper::toSummaryResponse);
    }

    /**
     * GET /disputes/freelancer-org/{orgId}/stats
     * Returns dispute statistics for a FreelancerOrg.
     */
    @GetMapping("/freelancer-org/{orgId}/stats")
    @Operation(summary = "Dispute stats for a FreelancerOrg")
    public Mono<DisputeResponses.DisputeStatsResponse> getDisputeStatsByOrg(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String orgId) {
        String tenantId = currentUser.tenantId().toString();
        return queryUseCase.getDisputeStatsByOrg(orgId, tenantId)
                .map(DisputeRestMapper::toStatsResponse);
    }

}
