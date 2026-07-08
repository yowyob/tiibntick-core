package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.CreateFreelancerRequest;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.FreelancerOrgLinkRequest;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.FreelancerProfileResponse;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.RateActorRequest;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.UpdateActorLocationRequest;
import com.yowyob.tiibntick.core.actor.application.command.AssociateFreelancerCommand;
import com.yowyob.tiibntick.core.actor.application.command.CreateFreelancerProfileCommand;
import com.yowyob.tiibntick.core.actor.application.command.DissociateFreelancerCommand;
import com.yowyob.tiibntick.core.actor.application.command.LinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.command.RateActorCommand;
import com.yowyob.tiibntick.core.actor.application.command.UnlinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.command.UpdateActorLocationCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IAssociateFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateFreelancerProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IDissociateFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindFreelancerByOrgUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.ILinkFreelancerOrgUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IRateActorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IUpdateActorLocationUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.AvailabilitySlot;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerRole;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.yowyob.tiibntick.common.api.ApiResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for freelancer profile operations.
 *
 * <p> — Migrated from {@code ReactiveRequestContextHolder} (Kernel pattern) to
 * {@code @CurrentUser TntUserIdentity} (tnt-auth-core pattern).
 *
 * <h3> additions — FreelancerOrganization link endpoints</h3>
 * <ul>
 *   <li>{@code POST /me/org} — manual link to a FreelancerOrganization (admin backup).</li>
 *   <li>{@code DELETE /me/org} — manual unlink from FreelancerOrganization.</li>
 *   <li>{@code GET /org/{orgId}/members} — list all members (OWNER + subs) of an org.</li>
 *   <li>{@code GET /org/{orgId}/owner} — get the OWNER actor of an org.</li>
 *   <li>{@code GET /org/{orgId}/sub-deliverers} — list sub-deliverer actors of an org.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@RestController
@Validated
@RequestMapping("/api/v1/freelancers")
@Tag(name = "Freelancers", description = "Freelancer profile management")
public class FreelancerController {

    private final ICreateFreelancerProfileUseCase createUseCase;
    private final IFindFreelancerUseCase findUseCase;
    private final IAssociateFreelancerUseCase associateUseCase;
    private final IDissociateFreelancerUseCase dissociateUseCase;
    private final IUpdateActorLocationUseCase updateLocationUseCase;
    private final IRateActorUseCase rateActorUseCase;
    private final ILinkFreelancerOrgUseCase linkOrgUseCase;
    private final IFindFreelancerByOrgUseCase findByOrgUseCase;

    public FreelancerController(ICreateFreelancerProfileUseCase createUseCase,
                                 IFindFreelancerUseCase findUseCase,
                                 IAssociateFreelancerUseCase associateUseCase,
                                 IDissociateFreelancerUseCase dissociateUseCase,
                                 IUpdateActorLocationUseCase updateLocationUseCase,
                                 IRateActorUseCase rateActorUseCase,
                                 ILinkFreelancerOrgUseCase linkOrgUseCase,
                                 IFindFreelancerByOrgUseCase findByOrgUseCase) {
        this.createUseCase = createUseCase;
        this.findUseCase = findUseCase;
        this.associateUseCase = associateUseCase;
        this.dissociateUseCase = dissociateUseCase;
        this.updateLocationUseCase = updateLocationUseCase;
        this.rateActorUseCase = rateActorUseCase;
        this.linkOrgUseCase = linkOrgUseCase;
        this.findByOrgUseCase = findByOrgUseCase;
    }

    // ── Existing endpoints (unchanged) ─────────────────────────────────────────

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a freelancer profile for the authenticated user")
    public Mono<ResponseEntity<ApiResponse<FreelancerProfileResponse>>> createFreelancerProfile(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody Mono<CreateFreelancerRequest> requestMono) {
        return requestMono.flatMap(req -> {
            List<ServiceZoneId> zones = req.serviceZoneIds().stream()
                    .map(ServiceZoneId::of).toList();
            List<AvailabilitySlot> slots = req.availabilitySlots() != null
                    ? req.availabilitySlots().stream().map(dto -> dto.toDomain()).toList()
                    : List.of();
            return createUseCase.createFreelancerProfile(new CreateFreelancerProfileCommand(
                    currentUser.tenantId(),
                    currentUser.userId(),
                    zones, slots,
                    req.pricingPolicyId()));
        })
        .map(FreelancerProfileResponse::from)
        .map(r -> ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(r)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Get the freelancer profile of the authenticated freelancer")
    public Mono<ResponseEntity<ApiResponse<FreelancerProfileResponse>>> getMyProfile(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return findUseCase.findByActorId(currentUser.tenantId(), currentUser.userId())
                .map(FreelancerProfileResponse::from)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/zone/{zoneId}/available")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List available freelancers in a service zone")
    public Flux<FreelancerProfileResponse> getAvailableInZone(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID zoneId) {
        return findUseCase.findAvailableInZone(currentUser.tenantId(), ServiceZoneId.of(zoneId))
                .map(FreelancerProfileResponse::from);
    }

    @GetMapping("/agency/{agencyId}")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    @Operation(summary = "List freelancers associated with a given agency")
    public Flux<FreelancerProfileResponse> getByAssociatedAgency(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID agencyId) {
        return findUseCase.findByAssociatedAgency(currentUser.tenantId(), agencyId)
                .map(FreelancerProfileResponse::from);
    }

    @PostMapping("/me/agencies/{agencyId}")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Associate the authenticated freelancer with an agency")
    public Mono<ResponseEntity<ApiResponse<FreelancerProfileResponse>>> associateWithAgency(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID agencyId) {
        return associateUseCase.associate(
                new AssociateFreelancerCommand(currentUser.tenantId(), currentUser.userId(), agencyId))
                .map(FreelancerProfileResponse::from)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @DeleteMapping("/me/agencies/{agencyId}")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Dissociate the authenticated freelancer from an agency")
    public Mono<ResponseEntity<ApiResponse<FreelancerProfileResponse>>> dissociateFromAgency(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID agencyId,
            @RequestParam(required = false) String reason) {
        return dissociateUseCase.dissociate(
                new DissociateFreelancerCommand(currentUser.tenantId(), currentUser.userId(),
                        agencyId, reason))
                .map(FreelancerProfileResponse::from)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PatchMapping("/me/location")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Update GPS location of the authenticated freelancer")
    public Mono<ResponseEntity<Void>> updateMyLocation(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody Mono<UpdateActorLocationRequest> requestMono) {
        return requestMono.flatMap(req ->
                updateLocationUseCase.updateLocation(new UpdateActorLocationCommand(
                        currentUser.tenantId(),
                        currentUser.userId(),
                        ActorType.FREELANCER,
                        req.latitude(),
                        req.longitude(),
                        req.accuracy(),
                        req.resolvedSource())))
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/{actorId}/rate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rate a freelancer after a completed delivery")
    public Mono<ResponseEntity<Void>> rateFreelancer(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID actorId,
            @Valid @RequestBody Mono<RateActorRequest> requestMono) {
        return requestMono.flatMap(req ->
                rateActorUseCase.rateActor(new RateActorCommand(
                        currentUser.tenantId(), actorId, ActorType.FREELANCER,
                        req.score(), req.ratedByActorId())))
                .thenReturn(ResponseEntity.noContent().build());
    }

    // FreelancerOrganization link endpoints ───────────────────────────

    /**
     * Manually links the authenticated freelancer to a FreelancerOrganization.
     *
     * <p>This endpoint is a fallback for cases where the Kafka event
     * ({@code tnt.freelancer_org.sub_deliverer.associated}) was not processed.
     * In the normal operational flow, linking is done automatically by
     * {@code FreelancerOrgEventConsumer}.
     *
     * <p>Role is inferred from the request context:
     * <ul>
     *   <li>If the authenticated actor created the org, role = OWNER.</li>
     *   <li>Otherwise, role = SUB_DELIVERER.</li>
     * </ul>
     * The role must be passed explicitly in the request body as the controller has
     * no org-domain context.
     */
    @PostMapping("/me/org")
    @PreAuthorize("hasAnyRole('FREELANCER', 'FREELANCER_OWNER', 'TNT_ADMIN')")
    @Operation(summary = "Manually link the authenticated freelancer to a FreelancerOrganization",
               description = "Backup endpoint. In production, linking is automatic via Kafka events.")
    public Mono<ResponseEntity<ApiResponse<FreelancerProfileResponse>>> linkToFreelancerOrg(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody Mono<FreelancerOrgLinkRequest> requestMono,
            @RequestParam(defaultValue = "SUB_DELIVERER") String role) {
        return requestMono.flatMap(req -> {
            FreelancerRole freelancerRole = FreelancerRole.from(role);
            if (freelancerRole == null) {
                return Mono.error(new IllegalArgumentException(
                        "Invalid role: " + role + ". Expected OWNER or SUB_DELIVERER."));
            }
            LinkFreelancerOrgCommand cmd = new LinkFreelancerOrgCommand(
                    currentUser.tenantId(),
                    currentUser.userId(),
                    req.freelancerOrgId(),
                    freelancerRole,
                    false);
            return linkOrgUseCase.linkToFreelancerOrg(cmd);
        })
        .map(FreelancerProfileResponse::from)
        .map(r -> ResponseEntity.ok(
                ApiResponse.success(r)));
    }

    /**
     * Manually unlinks the authenticated freelancer from their FreelancerOrganization.
     *
     * <p>This removes the {@code freelancerOrgId} and {@code roleInOrg} from the
     * actor profile. The org itself is not affected (org management is in
     * {@code tnt-organization-core}).
     */
    @DeleteMapping("/me/org")
    @PreAuthorize("hasAnyRole('FREELANCER', 'FREELANCER_OWNER', 'FREELANCER_SUB', 'TNT_ADMIN')")
    @Operation(summary = "Unlink the authenticated freelancer from their FreelancerOrganization")
    public Mono<ResponseEntity<ApiResponse<FreelancerProfileResponse>>> unlinkFromFreelancerOrg(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam UUID freelancerOrgId) {
        UnlinkFreelancerOrgCommand cmd = new UnlinkFreelancerOrgCommand(
                currentUser.tenantId(),
                currentUser.userId(),
                freelancerOrgId);
        return linkOrgUseCase.unlinkFromFreelancerOrg(cmd)
                .map(FreelancerProfileResponse::from)
                .map(r -> ResponseEntity.ok(
                        ApiResponse.success(r)));
    }

    /**
     * Returns the OWNER actor profile of a FreelancerOrganization.
     * Used by tnt-delivery-core and tnt-billing-wallet for mission assignment and splits.
     */
    @GetMapping("/org/{orgId}/owner")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the OWNER actor profile of a FreelancerOrganization")
    public Mono<ResponseEntity<ApiResponse<FreelancerProfileResponse>>> getOrgOwner(
            @PathVariable UUID orgId) {
        return findByOrgUseCase.findOwnerByOrg(orgId)
                .map(FreelancerProfileResponse::from)
                .map(r -> ResponseEntity.ok(
                        ApiResponse.success(r)));
    }

    /**
     * Returns all sub-deliverer actor profiles of a FreelancerOrganization.
     * Used by the OWNER dashboard and tnt-delivery-core for mission delegation.
     */
    @GetMapping("/org/{orgId}/sub-deliverers")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER', 'AGENCY_MANAGER', 'TNT_ADMIN')")
    @Operation(summary = "List sub-deliverer actor profiles of a FreelancerOrganization")
    public Flux<FreelancerProfileResponse> getOrgSubDeliverers(
            @PathVariable UUID orgId) {
        return findByOrgUseCase.findSubDeliverersByOrg(orgId)
                .map(FreelancerProfileResponse::from);
    }
}
