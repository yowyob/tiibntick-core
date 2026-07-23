package com.yowyob.tiibntick.core.administration.adapter.in.web;

import com.yowyob.tiibntick.core.administration.adapter.in.web.dto.request.UpdateTntPlatformOptionsRequest;
import com.yowyob.tiibntick.core.administration.adapter.in.web.dto.response.*;
import com.yowyob.tiibntick.core.administration.application.service.TntAdministrationApplicationService;
import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for TiiBnTick-specific administration endpoints.
 *
 * <p>Base path: {@code /api/v1/admin}
 *
 * <p>Covers:
 * <ul>
 *   <li>Permission catalog (TNT + Kernel integration keys, tnt-roles-core format)</li>
 *   <li>Role templates (static catalog including canonical TntRole templates)</li>
 *   <li>Role definitions (provisioned per-tenant, with kernelRoleId)</li>
 *   <li>Platform options (TiiBnTick feature flags per tenant)</li>
 * </ul>
 *
 * <p> — Migrated from manual {@code @RequestHeader("X-Tenant-Id")} / {@code @RequestHeader("X-User-Id")}
 * extraction to {@code @CurrentUser TntUserIdentity} (tnt-auth-core).
 * The {@link TntUserIdentity} is injected by {@code TntCurrentUserArgumentResolver} registered by
 * tnt-auth-core's auto-configuration. It provides tenant/user/actor IDs extracted from the JWT.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration", description = "TiiBnTick platform administration: permissions, roles, settings")
@SecurityRequirement(name = "bearerAuth")
public class TntAdministrationController {

    private final TntAdministrationApplicationService service;
    private final com.yowyob.tiibntick.core.administration.application.service.FreelancerOrgAdminService freelancerOrgAdminService;

    public TntAdministrationController(TntAdministrationApplicationService service,
            com.yowyob.tiibntick.core.administration.application.service.FreelancerOrgAdminService freelancerOrgAdminService) {
        this.service = service;
        this.freelancerOrgAdminService = freelancerOrgAdminService;
    }

    // ─── Permissions ─────────────────────────────────────────────────────────────

    /**
     * Lists all TiiBnTick permissions from the enriched catalog (legacy + tnt-roles-core format).
     * Response includes kernelPermissionId when resolved.
     * Optionally filtered by module.
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('administration:permissions:read')")
    @Operation(summary = "List all TiiBnTick permissions (optionally filtered by module)")
    public Mono<ResponseEntity<List<TntPermissionResponse>>> listPermissions(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(required = false) String module) {
        return service.listByModule(module)
                .map(TntPermissionResponse::from)
                .collectList()
                .map(ResponseEntity::ok);
    }

    /**
     * Resolves the Kernel permission UUID for a given TNT permission code.
     * Queries the Kernel bridge — may return kernelPermissionId=null for TNT-exclusive codes.
     */
    @GetMapping("/permissions/{code}/kernel-resolve")
    @PreAuthorize("hasAuthority('administration:permissions:read')")
    @Operation(summary = "Resolve Kernel UUID for a TiiBnTick permission code")
    public Mono<ResponseEntity<TntPermissionResponse>> resolvePermissionKernelId(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String code) {
        return service.resolveKernelPermissionId(code)
                .map(TntPermissionResponse::from)
                .map(ResponseEntity::ok);
    }

    // ─── Role Templates ──────────────────────────────────────────────────────────

    /**
     * Lists all TNT role templates (operational + canonical TntRole templates).
     * Static catalog — no kernelRoleId returned here (use /role-definitions for per-tenant data).
     */
    @GetMapping("/role-templates")
    @PreAuthorize("hasAuthority('administration:roles:read')")
    @Operation(summary = "List all TiiBnTick role templates (static catalog)")
    public Mono<ResponseEntity<List<TntRoleTemplateResponse>>> listRoleTemplates(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return service.listTemplates()
                .map(TntRoleTemplateResponse::from)
                .collectList()
                .map(ResponseEntity::ok);
    }

    /**
     * Provisions all TNT role templates for the caller's tenant.
     *
     * <p> — Also triggers tnt-roles-core's
     * {@code TntRoleInitializationService.provisionForTenant()} for the 9 canonical roles.
     * This endpoint is idempotent — already-provisioned templates are skipped.
     */
    @PostMapping("/role-templates/provision")
    @PreAuthorize("hasAuthority('administration:roles:write')")
    @Operation(summary = "Provision all TNT role templates for the current tenant (idempotent)")
    public Mono<ResponseEntity<Void>> provisionRoleTemplates(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID tenantId      = currentUser.tenantId();
        UUID organizationId = currentUser.organizationId() != null
                ? currentUser.organizationId()
                : currentUser.userId();
        UUID userId        = currentUser.userId();
        return service.provisionForTenant(tenantId, organizationId, userId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    // ─── Role Definitions ─────────────────────────────────────────────────────────

    /**
     * Lists all TntRoleDefinitions provisioned for the caller's tenant.
     * Includes kernelRoleId and kernelSynced status.
     */
    @GetMapping("/role-definitions")
    @PreAuthorize("hasAuthority('administration:roles:read')")
    @Operation(summary = "List all provisioned role definitions for the current tenant")
    public Mono<ResponseEntity<List<TntRoleDefinitionResponse>>> listRoleDefinitions(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return service.listByTenant(currentUser.tenantId())
                .map(TntRoleDefinitionResponse::from)
                .collectList()
                .map(ResponseEntity::ok);
    }

    /**
     * Gets a specific TntRoleDefinition by its UUID.
     */
    @GetMapping("/role-definitions/{definitionId}")
    @PreAuthorize("hasAuthority('administration:roles:read')")
    @Operation(summary = "Get a specific role definition by ID")
    public Mono<ResponseEntity<TntRoleDefinitionResponse>> getRoleDefinition(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID definitionId) {
        return service.getById(definitionId)
                .map(TntRoleDefinitionResponse::from)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ─── Platform Options ─────────────────────────────────────────────────────────

    /**
     * Gets TiiBnTick platform feature options for the caller's tenant.
     * Creates default options if none exist yet.
     */
    @GetMapping("/settings/tnt-platform-options")
    @PreAuthorize("hasAuthority('administration:settings:read')")
    @Operation(summary = "Get TiiBnTick platform options for the current tenant")
    public Mono<ResponseEntity<TntPlatformOptionsResponse>> getPlatformOptions(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return service.getPlatformOptions(currentUser.tenantId())
                .map(o -> ResponseEntity.ok(TntPlatformOptionsResponse.from(o)));
    }

    /**
     * Updates TiiBnTick platform feature options for the caller's tenant.
     * Requires {@code administration:settings:write} permission.
     */
    @PutMapping("/settings/tnt-platform-options")
    @PreAuthorize("hasAuthority('administration:settings:write')")
    @Operation(summary = "Update TiiBnTick platform options for the current tenant")
    public Mono<ResponseEntity<TntPlatformOptionsResponse>> updatePlatformOptions(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody UpdateTntPlatformOptionsRequest req) {
        UUID tenantId = currentUser.tenantId();
        return service.getPlatformOptions(tenantId)
                .flatMap(existing -> {
                    TntPlatformOptions updated = TntPlatformOptions.rehydrate(
                            existing.getId(), tenantId,
                            req.blockchainEnabled(), req.smartDisputeResolutionEnabled(),
                            req.blockchainNetwork(), req.freelancerModeEnabled(),
                            req.requireFreelancerApproval(), req.maxFreelancerConcurrentMissions(),
                            req.pointRelaisModeEnabled(), req.relayPointMaxStorageHours(),
                            req.announcementMarketplaceEnabled(), req.maxCourierAnnouncementResponses(),
                            req.tvaRate(), req.defaultCurrency(),
                            req.disputeManagementEnabled(), req.disputeFilingWindowDays(),
                            existing.getCreatedAt(), Instant.now());
                    return service.updatePlatformOptions(tenantId, updated);
                })
                .map(o -> ResponseEntity.ok(TntPlatformOptionsResponse.from(o)));
    }

    /**
     * Initializes default TiiBnTick platform options for the caller's tenant.
     * Only available to platform admins. Idempotent — returns existing options if already initialized.
     */
    @PostMapping("/settings/tnt-platform-options/initialize")
    @PreAuthorize("hasAuthority('tnt:platform:admin')")
    @Operation(summary = "Initialize default platform options for the current tenant (platform admin only)")
    public Mono<ResponseEntity<TntPlatformOptionsResponse>> initializeOptions(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return service.initializeDefaultOptions(currentUser.tenantId())
                .map(o -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(TntPlatformOptionsResponse.from(o)));
    }
    // ─── FreelancerOrg Administration () ──────────────────────────────────────

    /**
     * POST /api/v1/admin/freelancer-orgs/{orgId}/kyc/basic/approve
     * Approves basic KYC for a FreelancerOrg.
     */
    @PostMapping("/freelancer-orgs/{orgId}/kyc/basic/approve")
    @PreAuthorize("hasAnyRole('TNT_ADMIN','SUPPORT_AGENT')")
    @Operation(summary = "Approve basic KYC for a FreelancerOrg")
    public Mono<ResponseEntity<Void>> approveKycBasic(
            @PathVariable String orgId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return freelancerOrgAdminService.approveKycBasic(currentUser.tenantId().toString(), orgId,
                        currentUser.actorId().toString())
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * POST /api/v1/admin/freelancer-orgs/{orgId}/kyc/full/approve
     * Approves full KYC for a FreelancerOrg.
     */
    @PostMapping("/freelancer-orgs/{orgId}/kyc/full/approve")
    @PreAuthorize("hasAnyRole('TNT_ADMIN','SUPPORT_AGENT')")
    @Operation(summary = "Approve full KYC for a FreelancerOrg")
    public Mono<ResponseEntity<Void>> approveKycFull(
            @PathVariable String orgId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return freelancerOrgAdminService.approveKycFull(currentUser.tenantId().toString(), orgId,
                        currentUser.actorId().toString())
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * POST /api/v1/admin/freelancer-orgs/{orgId}/kyc/reject
     * Rejects KYC verification with a reason.
     */
    @PostMapping("/freelancer-orgs/{orgId}/kyc/reject")
    @PreAuthorize("hasAnyRole('TNT_ADMIN','SUPPORT_AGENT')")
    @Operation(summary = "Reject KYC verification for a FreelancerOrg")
    public Mono<ResponseEntity<Void>> rejectKyc(
            @PathVariable String orgId,
            @RequestParam String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return freelancerOrgAdminService.rejectKyc(currentUser.tenantId().toString(), orgId,
                        currentUser.actorId().toString(), reason)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * POST /api/v1/admin/freelancer-orgs/{orgId}/suspend
     * Suspends a FreelancerOrg.
     */
    @PostMapping("/freelancer-orgs/{orgId}/suspend")
    @PreAuthorize("hasAnyRole('TNT_ADMIN','SUPPORT_AGENT')")
    @Operation(summary = "Suspend a FreelancerOrg")
    public Mono<ResponseEntity<Void>> suspendFreelancerOrg(
            @PathVariable String orgId,
            @RequestParam String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return freelancerOrgAdminService.suspendFreelancerOrg(currentUser.tenantId().toString(), orgId,
                        currentUser.actorId().toString(), reason)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * POST /api/v1/admin/freelancer-orgs/{orgId}/unsuspend
     * Unsuspends a FreelancerOrg.
     */
    @PostMapping("/freelancer-orgs/{orgId}/unsuspend")
    @PreAuthorize("hasAnyRole('TNT_ADMIN','SUPPORT_AGENT')")
    @Operation(summary = "Unsuspend a FreelancerOrg")
    public Mono<ResponseEntity<Void>> unsuspendFreelancerOrg(
            @PathVariable String orgId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return freelancerOrgAdminService.unsuspendFreelancerOrg(currentUser.tenantId().toString(), orgId,
                        currentUser.actorId().toString())
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * POST /api/v1/admin/freelancer-orgs/{orgId}/blacklist
     * Permanently blacklists a FreelancerOrg.
     */
    @PostMapping("/freelancer-orgs/{orgId}/blacklist")
    @PreAuthorize("hasRole('TNT_ADMIN')")
    @Operation(summary = "Permanently blacklist a FreelancerOrg (irreversible)")
    public Mono<ResponseEntity<Void>> blacklistFreelancerOrg(
            @PathVariable String orgId,
            @RequestParam String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return freelancerOrgAdminService.blacklistFreelancerOrg(currentUser.tenantId().toString(), orgId,
                        currentUser.actorId().toString(), reason)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

}