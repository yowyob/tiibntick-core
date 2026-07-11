package com.yowyob.tiibntick.core.delivery.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.request.UpdateLocationRequest;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.response.DeliveryDetailResponse;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.response.DeliveryResponseMapper;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.response.EtaResponse;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.*;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.FreelancerRole;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for delivery lifecycle management.
 *
 * <p> — Migrated from {@code @RequestHeader("X-Delivery-Person-Id")} to
 * {@code @CurrentUser TntUserIdentity} (tnt-auth-core).
 * The delivery person ID is resolved from the JWT claim {@code actor} (enriched
 * by {@code ActorCoreYowAuthTntAdapter}) or falls back to the JWT subject ({@code userId}).
 *
 * <p>The tenant ID remains as {@code @PathVariable} to preserve the URL structure
 * {@code /api/v1/tenants/{tenantId}/deliveries}, while the JWT tenant is used for
 * security validation. Both must match for tenant-scoped operations.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Delivery Lifecycle", description = "State machine transitions: pickup → transit → delivered")
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/deliveries")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {

    private final DeliveryLifecycleUseCase lifecycleUseCase;
    private final DeliveryQueryUseCase queryUseCase;

    @Operation(summary = "Get delivery by ID")
    @GetMapping("/{deliveryId}")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    public Mono<DeliveryDetailResponse> getById(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId) {
        return queryUseCase.findDeliveryById(tenantId, deliveryId)
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "Track delivery by tracking code (public endpoint)")
    @GetMapping("/track/{trackingCode}")
    public Mono<DeliveryDetailResponse> track(@PathVariable String trackingCode) {
        return queryUseCase.findByTrackingCode(trackingCode)
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "List deliveries by sender")
    @GetMapping("/sender/{senderId}")
    @PreAuthorize("hasAnyRole('CLIENT','AGENCY_MANAGER','SUPPORT_AGENT','TNT_ADMIN')")
    public Flux<DeliveryDetailResponse> listBySender(
            @PathVariable UUID tenantId,
            @PathVariable UUID senderId) {
        return queryUseCase.findDeliveriesBySender(tenantId, senderId)
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "List deliveries by delivery person")
    @GetMapping("/delivery-person/{deliveryPersonId}")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','TNT_ADMIN')")
    public Flux<DeliveryDetailResponse> listByDeliveryPerson(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryPersonId) {
        return queryUseCase.findDeliveriesByDeliveryPerson(tenantId, deliveryPersonId)
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "List deliveries by status")
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    public Flux<DeliveryDetailResponse> listByStatus(
            @PathVariable UUID tenantId,
            @PathVariable DeliveryStatus status) {
        return queryUseCase.findDeliveriesByStatus(tenantId, status)
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "Delivery person confirms parcel pickup")
    @PostMapping("/{deliveryId}/pickup")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER')")
    public Mono<DeliveryDetailResponse> confirmPickup(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID deliveryPersonId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return lifecycleUseCase.confirmPickup(
                        new ConfirmPickupCommand(tenantId, deliveryId, deliveryPersonId))
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "Delivery person starts transit")
    @PostMapping("/{deliveryId}/transit/start")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER')")
    public Mono<DeliveryDetailResponse> startTransit(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody(required = false) UpdateLocationRequest loc) {
        UUID deliveryPersonId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        var coords = loc != null ? loc.toCoordinates() : null;
        return lifecycleUseCase.startTransit(
                        new StartTransitCommand(tenantId, deliveryId, deliveryPersonId, coords))
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "Deposit parcel at relay point")
    @PostMapping("/{deliveryId}/relay/{relayPointId}/deposit")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','RELAY_OPERATOR')")
    public Mono<DeliveryDetailResponse> depositAtRelay(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @PathVariable UUID relayPointId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID deliveryPersonId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return lifecycleUseCase.depositAtRelayPoint(
                        new DepositAtRelayPointCommand(tenantId, deliveryId, deliveryPersonId, relayPointId))
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "Resume transit from relay point")
    @PostMapping("/{deliveryId}/relay/resume")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','RELAY_OPERATOR')")
    public Mono<DeliveryDetailResponse> resumeFromRelay(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID deliveryPersonId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return lifecycleUseCase.resumeFromRelayPoint(
                        new ResumeFromRelayPointCommand(tenantId, deliveryId, deliveryPersonId))
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "Update real-time delivery person location (Kalman ETA refinement)")
    @PostMapping("/{deliveryId}/location")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER')")
    public Mono<EtaResponse> updateLocation(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody UpdateLocationRequest req) {
        UUID deliveryPersonId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return lifecycleUseCase.updateLocation(
                        new UpdateDeliveryLocationCommand(tenantId, deliveryId,
                                deliveryPersonId, req.toCoordinates()))
                .map(DeliveryResponseMapper::toEtaResponse);
    }

    @Operation(summary = "Mark delivery as completed")
    @PostMapping("/{deliveryId}/complete")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER')")
    public Mono<DeliveryDetailResponse> complete(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(required = false) String proofPhotoUrl,
            @RequestParam(required = false) String photoHash,
            @RequestParam(required = false) String signatureHash,
            @RequestParam(required = false) Double gpsLat,
            @RequestParam(required = false) Double gpsLng) {
        UUID deliveryPersonId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return lifecycleUseCase.completeDelivery(
                        new CompleteDeliveryCommand(tenantId, deliveryId, deliveryPersonId, proofPhotoUrl,
                                photoHash, signatureHash, gpsLat, gpsLng))
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "Mark delivery as failed")
    @PostMapping("/{deliveryId}/fail")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER')")
    public Mono<DeliveryDetailResponse> fail(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam String reason) {
        UUID deliveryPersonId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return lifecycleUseCase.failDelivery(
                        new FailDeliveryCommand(tenantId, deliveryId, deliveryPersonId, reason))
                .map(DeliveryResponseMapper::toDetail);
    }

    @Operation(summary = "Cancel a delivery")
    @PostMapping("/{deliveryId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('CLIENT','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<Void> cancel(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam String reason) {

        UUID requesterId = currentUser.userId();
        return lifecycleUseCase.cancelDelivery(
                        new CancelDeliveryCommand(tenantId, deliveryId, requesterId, reason))
                .then();
    }

    // ── : FreelancerOrg endpoints ─────────────────────────────────────────

    /**
     * Assigns a FreelancerOrganization as the executor of this delivery.
     *
     * POST /api/v1/tenants/{tenantId}/deliveries/{deliveryId}/assign-freelancer
     */
    @Operation(summary = "Assign a FreelancerOrg to execute a delivery ()",
               description = "Sets the FreelancerOrg and role (OWNER/SUB_DELIVERER) for this delivery. "
                           + "Updates platform to FREELANCER.")
    @PostMapping("/{deliveryId}/assign-freelancer")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','FREELANCER_OWNER','TNT_ADMIN')")
    public Mono<DeliveryDetailResponse> assignFreelancerOrg(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryId,
            @RequestParam String freelancerOrgId,
            @RequestParam(defaultValue = "OWNER") FreelancerRole freelancerRole) {

        return lifecycleUseCase.assignToFreelancerOrg(
                new AssignFreelancerOrgCommand(deliveryId, tenantId, freelancerOrgId, freelancerRole))
                .map(DeliveryResponseMapper::toDetail);
    }

    /**
     * Lists deliveries assigned to a specific FreelancerOrg.
     *
     * GET /api/v1/tenants/{tenantId}/deliveries/by-freelancer?orgId=...
     */
    @Operation(summary = "List deliveries by FreelancerOrg ()",
               description = "Returns all deliveries assigned to the given FreelancerOrg.")
    @GetMapping("/by-freelancer")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','AGENCY_MANAGER','TNT_ADMIN')")
    public reactor.core.publisher.Flux<DeliveryDetailResponse> listByFreelancerOrg(
            @PathVariable UUID tenantId,
            @RequestParam String orgId) {

        return queryUseCase.listByFreelancerOrgId(orgId)
                .map(DeliveryResponseMapper::toDetail);
    }
}
