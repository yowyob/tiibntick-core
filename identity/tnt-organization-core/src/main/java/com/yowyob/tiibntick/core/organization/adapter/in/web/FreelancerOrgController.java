package com.yowyob.tiibntick.core.organization.adapter.in.web;

import com.yowyob.tiibntick.core.organization.application.port.in.ManageFreelancerOrgUseCase;
import com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization;
import com.yowyob.tiibntick.core.organization.domain.vo.AssociatedDelivererRef;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller for FreelancerOrganization lifecycle management.
 *
 * <p>A FreelancerOrganization represents an independent delivery operator
 * (individual or small collective) with its own KYC, capabilities, service zones,
 * and optional sub-deliverer network.
 *
 * <p>URL structure: {@code /api/v1/freelancer-orgs}
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Freelancer Organization", description = "Registration, KYC, lifecycle, and sub-deliverer management")
@RestController
@RequestMapping("/api/v1/freelancer-orgs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FreelancerOrgController {

    private final ManageFreelancerOrgUseCase manageFreelancerOrgUseCase;

    // ─── Query endpoints ───────────────────────────────────────────────────────

    @Operation(summary = "Get FreelancerOrganization by internal ID")
    @GetMapping("/{orgId}")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<FreelancerOrganization> getById(@PathVariable UUID orgId) {
        return manageFreelancerOrgUseCase.findById(OrganizationId.of(orgId));
    }

    @Operation(summary = "Get FreelancerOrganization by owner actor ID")
    @GetMapping("/by-owner/{ownerActorId}")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<FreelancerOrganization> getByOwner(@PathVariable UUID ownerActorId) {
        return manageFreelancerOrgUseCase.findByOwnerActorId(ownerActorId);
    }

    @Operation(summary = "Find available FreelancerOrganizations near a geographic point")
    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('CLIENT','AGENCY_MANAGER','TNT_ADMIN')")
    public Flux<FreelancerOrganization> findAvailableInZone(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10.0") double radiusKm) {
        return manageFreelancerOrgUseCase.findAvailableInZone(latitude, longitude, radiusKm);
    }

    @Operation(summary = "List sub-deliverers of a FreelancerOrganization")
    @GetMapping("/{orgId}/sub-deliverers")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','AGENCY_MANAGER','TNT_ADMIN')")
    public Flux<AssociatedDelivererRef> listSubDeliverers(@PathVariable UUID orgId) {
        return manageFreelancerOrgUseCase.listSubDeliverers(OrganizationId.of(orgId));
    }

    // ─── Registration ──────────────────────────────────────────────────────────

    @Operation(summary = "Register a new FreelancerOrganization")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','TNT_ADMIN')")
    public Mono<FreelancerOrganization> register(
            @Valid @RequestBody RegisterFreelancerOrgRequest request) {
        return manageFreelancerOrgUseCase.registerFreelancerOrg(
                request.organizationId(),
                request.ownerActorId(),
                request.tradeName());
    }

    // ─── KYC lifecycle ─────────────────────────────────────────────────────────

    @Operation(summary = "Upgrade KYC to BASIC (national ID validated)")
    @PostMapping("/{orgId}/kyc/basic")
    @PreAuthorize("hasAnyRole('TNT_ADMIN','SUPPORT_AGENT')")
    public Mono<FreelancerOrganization> upgradeKycToBasic(@PathVariable UUID orgId) {
        return manageFreelancerOrgUseCase.upgradeKycToBasic(OrganizationId.of(orgId));
    }

    @Operation(summary = "Upgrade KYC to FULL (vehicle registration + insurance validated)")
    @PostMapping("/{orgId}/kyc/full")
    @PreAuthorize("hasAnyRole('TNT_ADMIN','SUPPORT_AGENT')")
    public Mono<FreelancerOrganization> upgradeKycToFull(@PathVariable UUID orgId) {
        return manageFreelancerOrgUseCase.upgradeKycToFull(OrganizationId.of(orgId));
    }

    // ─── Admin lifecycle ───────────────────────────────────────────────────────

    @Operation(summary = "Submit FreelancerOrganization for admin review")
    @PostMapping("/{orgId}/submit")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','TNT_ADMIN')")
    public Mono<FreelancerOrganization> submitForReview(@PathVariable UUID orgId) {
        return manageFreelancerOrgUseCase.submitForReview(OrganizationId.of(orgId));
    }

    @Operation(summary = "Verify (approve) a FreelancerOrganization — triggers DID issuance")
    @PostMapping("/{orgId}/verify")
    @PreAuthorize("hasAnyRole('TNT_ADMIN')")
    public Mono<FreelancerOrganization> verify(
            @PathVariable UUID orgId,
            @RequestParam UUID adminActorId) {
        return manageFreelancerOrgUseCase.verifyFreelancerOrg(OrganizationId.of(orgId), adminActorId);
    }

    @Operation(summary = "Activate a verified FreelancerOrganization")
    @PostMapping("/{orgId}/activate")
    @PreAuthorize("hasAnyRole('TNT_ADMIN')")
    public Mono<FreelancerOrganization> activate(@PathVariable UUID orgId) {
        return manageFreelancerOrgUseCase.activateFreelancerOrg(OrganizationId.of(orgId));
    }

    @Operation(summary = "Suspend a FreelancerOrganization (temporary non-compliance)")
    @PostMapping("/{orgId}/suspend")
    @PreAuthorize("hasAnyRole('TNT_ADMIN','SUPPORT_AGENT')")
    public Mono<FreelancerOrganization> suspend(
            @PathVariable UUID orgId,
            @RequestParam String reason,
            @RequestParam(required = false) UUID adminActorId) {
        return manageFreelancerOrgUseCase.suspendFreelancerOrg(
                OrganizationId.of(orgId), reason, adminActorId);
    }

    @Operation(summary = "Reactivate a suspended FreelancerOrganization")
    @PostMapping("/{orgId}/unsuspend")
    @PreAuthorize("hasAnyRole('TNT_ADMIN')")
    public Mono<FreelancerOrganization> unsuspend(@PathVariable UUID orgId) {
        return manageFreelancerOrgUseCase.unsuspendFreelancerOrg(OrganizationId.of(orgId));
    }

    @Operation(summary = "Permanently blacklist a FreelancerOrganization")
    @PostMapping("/{orgId}/blacklist")
    @PreAuthorize("hasAnyRole('TNT_ADMIN')")
    public Mono<FreelancerOrganization> blacklist(
            @PathVariable UUID orgId,
            @RequestParam UUID adminActorId) {
        return manageFreelancerOrgUseCase.blacklistFreelancerOrg(OrganizationId.of(orgId), adminActorId);
    }

    // ─── Sub-deliverer management ──────────────────────────────────────────────

    @Operation(summary = "Invite a sub-deliverer to join the FreelancerOrganization")
    @PostMapping("/{orgId}/sub-deliverers/invite")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','TNT_ADMIN')")
    public Mono<AssociatedDelivererRef> inviteSubDeliverer(
            @PathVariable UUID orgId,
            @Valid @RequestBody InviteSubDelivererRequest request) {
        return manageFreelancerOrgUseCase.inviteSubDeliverer(
                OrganizationId.of(orgId),
                request.delivererActorId(),
                request.commissionRate());
    }

    @Operation(summary = "Accept a sub-deliverer invitation")
    @PostMapping("/{orgId}/sub-deliverers/{delivererActorId}/accept")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','FREELANCER_OWNER','TNT_ADMIN')")
    public Mono<AssociatedDelivererRef> acceptInvitation(
            @PathVariable UUID orgId,
            @PathVariable UUID delivererActorId) {
        return manageFreelancerOrgUseCase.acceptSubDelivererInvitation(
                OrganizationId.of(orgId), delivererActorId);
    }

    @Operation(summary = "Revoke a sub-deliverer association")
    @PostMapping("/{orgId}/sub-deliverers/{delivererActorId}/revoke")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','TNT_ADMIN')")
    public Mono<AssociatedDelivererRef> revokeSubDeliverer(
            @PathVariable UUID orgId,
            @PathVariable UUID delivererActorId) {
        return manageFreelancerOrgUseCase.revokeSubDeliverer(
                OrganizationId.of(orgId), delivererActorId);
    }

    // ─── Request DTOs ──────────────────────────────────────────────────────────

    /**
     * Request body for FreelancerOrganization registration.
     *
     * @param organizationId Optional Kernel org UUID (nullable for direct registration)
     * @param ownerActorId   OWNER actor UUID (must exist in tnt-actor-core)
     * @param tradeName      Commercial trade name
     */
    public record RegisterFreelancerOrgRequest(
            UUID organizationId,
            UUID ownerActorId,
            @NotBlank String tradeName
    ) {}

    /**
     * Request body for inviting a sub-deliverer.
     *
     * @param delivererActorId UUID of the actor to invite
     * @param commissionRate   Commission rate in [0.0, 1.0]
     */
    public record InviteSubDelivererRequest(
            UUID delivererActorId,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal commissionRate
    ) {}
}
