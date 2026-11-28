package com.yowyob.tiibntick.core.geo.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageHubApprovalUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Administration of relay hubs by the TiiBnTick administrator (decisions D-129, D-138, D-141).
 *
 * <p>Approval (D-141), permission {@code relay:approve} (granted to no role: platform administrator only), each 200
 * with the hub's approval and operating statuses (also when the hub already has the requested status), 404 unknown
 * hub, 409 transition refused by the machine (D-138):
 * <ul>
 *   <li>{@code POST .../approve}: PENDING → APPROVED;</li>
 *   <li>{@code POST .../reject}, body {@code {"reason": "..."}} mandatory: PENDING → REJECTED;</li>
 *   <li>{@code POST .../suspend}, body {@code {"reason": "..."}} optional: APPROVED → SUSPENDED;</li>
 *   <li>{@code POST .../reactivate}: SUSPENDED → APPROVED.</li>
 * </ul>
 * A Go-Freelancer relay point backed by the hub copies the change and its operator is notified.
 *
 * <p>Revocation (D-129, D-135):
 * <p>{@code POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/revoke}, permission {@code relay:revoke} (platform
 * administrator only), body {@code {"reason": "..."}} (mandatory). 200 with the revoked hub and the parcels still in
 * custody; 400 reason missing; 404 unknown hub; 409 when the hub is neither APPROVED nor SUSPENDED (D-135, D-138),
 * already revoked included. It keeps its own permission {@code relay:revoke} (also platform administrator only).
 */
@Tag(name = "Geo Services", description = "Relay hub administration")
@RestController
@RequestMapping("/api/v1/geo/tenants/{tenantId}/hubs/{hubId}")
@SecurityRequirement(name = "bearerAuth")
public class RelayHubAdminController {

    private final IRevokeRelayHubUseCase revokeUseCase;
    private final IManageHubApprovalUseCase approvalUseCase;

    public RelayHubAdminController(IRevokeRelayHubUseCase revokeUseCase, IManageHubApprovalUseCase approvalUseCase) {
        this.revokeUseCase = revokeUseCase;
        this.approvalUseCase = approvalUseCase;
    }

    @Operation(summary = "Approve a pending relay hub (administrator)")
    @PostMapping("/approve")
    @RequirePermission(resource = "relay", action = "approve")
    public Mono<RelayHubApprovalResponse> approve(@PathVariable UUID tenantId, @PathVariable UUID hubId,
                                                  @CurrentUser(required = false) TntSecurityContext ctx) {
        return admin(ctx).flatMap(a -> approvalUseCase.approve(tenantId, hubId, a)).map(RelayHubApprovalResponse::of);
    }

    @Operation(summary = "Reject a pending relay hub (administrator, mandatory reason)")
    @PostMapping("/reject")
    @RequirePermission(resource = "relay", action = "approve")
    public Mono<RelayHubApprovalResponse> reject(@PathVariable UUID tenantId, @PathVariable UUID hubId,
                                                 @Valid @RequestBody RejectRelayHubRequest request,
                                                 @CurrentUser(required = false) TntSecurityContext ctx) {
        return admin(ctx).flatMap(a -> approvalUseCase.reject(tenantId, hubId, request.reason(), a))
                .map(RelayHubApprovalResponse::of);
    }

    @Operation(summary = "Suspend an approved relay hub (administrator, optional reason)")
    @PostMapping("/suspend")
    @RequirePermission(resource = "relay", action = "approve")
    public Mono<RelayHubApprovalResponse> suspend(@PathVariable UUID tenantId, @PathVariable UUID hubId,
                                                  @Valid @RequestBody(required = false) SuspendRelayHubRequest request,
                                                  @CurrentUser(required = false) TntSecurityContext ctx) {
        String reason = request == null ? null : request.reason();
        return admin(ctx).flatMap(a -> approvalUseCase.suspend(tenantId, hubId, reason, a))
                .map(RelayHubApprovalResponse::of);
    }

    @Operation(summary = "Reactivate a suspended relay hub (administrator)")
    @PostMapping("/reactivate")
    @RequirePermission(resource = "relay", action = "approve")
    public Mono<RelayHubApprovalResponse> reactivate(@PathVariable UUID tenantId, @PathVariable UUID hubId,
                                                     @CurrentUser(required = false) TntSecurityContext ctx) {
        return admin(ctx).flatMap(a -> approvalUseCase.reactivate(tenantId, hubId, a)).map(RelayHubApprovalResponse::of);
    }

    private static Mono<UUID> admin(TntSecurityContext ctx) {
        return ctx == null || ctx.userId() == null
                ? Mono.error(new AccessDeniedException("An authenticated administrator is required"))
                : Mono.just(ctx.userId());
    }

    @Operation(summary = "Revoke a relay hub for good (administrator, mandatory reason, operator notified)")
    @PostMapping("/revoke")
    @RequirePermission(resource = "relay", action = "revoke")
    public Mono<RelayHubRevocationResponse> revoke(@PathVariable UUID tenantId, @PathVariable UUID hubId,
                                                   @Valid @RequestBody RevokeRelayHubRequest request,
                                                   @CurrentUser(required = false) TntSecurityContext ctx) {
        if (ctx == null || ctx.userId() == null) {
            return Mono.error(new AccessDeniedException("An authenticated administrator is required"));
        }
        return revokeUseCase.revoke(tenantId, hubId, request.reason(), ctx.userId())
                .map(RelayHubRevocationResponse::of);
    }

    /** Body of the rejection: the reason is mandatory (D-141). */
    public record RejectRelayHubRequest(@NotBlank @Size(max = RelayHub.MAX_REASON_LENGTH) String reason) { }

    /** Body of the suspension: the reason is optional. */
    public record SuspendRelayHubRequest(@Size(max = RelayHub.MAX_REASON_LENGTH) String reason) { }

    /** The hub after an approval change. */
    public record RelayHubApprovalResponse(UUID hubId, UUID tenantId, String approvalStatus, String operatingStatus,
                                           Instant updatedAt) {
        static RelayHubApprovalResponse of(RelayHub hub) {
            return new RelayHubApprovalResponse(hub.id(), hub.tenantId(), hub.approvalStatus().name(),
                    hub.status().name(), hub.updatedAt());
        }
    }

    /** Body of the revocation: the reason is mandatory (D-129). */
    public record RevokeRelayHubRequest(@NotBlank @Size(max = RelayHub.MAX_REASON_LENGTH) String reason) { }

    /** The revoked hub and the parcels it still held (to return or transfer: not wired yet, lot L2 of the events workstream). */
    public record RelayHubRevocationResponse(UUID hubId, UUID tenantId, String approvalStatus, String operatingStatus,
                                             String reason,
                                             Instant revokedAt, UUID revokedByActorId,
                                             boolean custodyKnown, List<HubCustodyItem> parcelsInCustody) {
        static RelayHubRevocationResponse of(RelayHubRevocation r) {
            RelayHub hub = r.hub();
            return new RelayHubRevocationResponse(hub.id(), hub.tenantId(), hub.approvalStatus().name(),
                    hub.status().name(),
                    hub.revocationReason(), hub.revokedAt(), hub.revokedByActorId(),
                    r.custodyKnown(), r.parcelsInCustody());
        }
    }
}
