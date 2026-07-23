package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Inbound REST adapter for billing policy lifecycle management.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@code POST /{policyId}/assign-org} — assigns policy to a FreelancerOrg or other actor</li>
 *   <li>{@code GET /owner/{ownerActorId}} — lists policies by owner actor ID</li>
 *   <li>{@code POST /from-template} — creates a policy from a named template</li>
 * </ul>
 *
 * <h3>Security (Audit n°7 · #5 / #6)</h3>
 * <p>Single-resource endpoints ({@code getById}, {@code activate}, {@code deactivate},
 * {@code archive}, {@code delete}, {@code assign-org}) resolve the tenant from the JWT
 * security context via {@code @CurrentUser TntUserIdentity} and pass it down to
 * {@link IBillingPolicyUseCase}, which scopes the repository lookup with
 * {@code findByIdAndTenantId} — closing an IDOR that previously let any caller read or
 * mutate another tenant's billing policy by ID alone. Mutation endpoints are additionally
 * gated with {@code @PreAuthorize("isAuthenticated()")} as a minimum baseline: no dedicated
 * {@code billing-policy:*} permission exists yet in the {@code tnt-roles-core} role catalog
 * (only generic {@code billing:read}/{@code billing:write}), so fine-grained
 * {@code @RequirePermission} enforcement is deferred until that catalog is extended.</p>
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/billing/policies")
@RequiredArgsConstructor
@Tag(name = "Billing Policy", description = "BillingPolicy lifecycle management API")
public class BillingPolicyController {

    private final IBillingPolicyUseCase policyUseCase;

    // ── Standard CRUD ─────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new billing policy")
    public Mono<BillingPolicyResponse> create(@Valid @RequestBody CreateBillingPolicyRequest req) {
        return policyUseCase.createPolicy(RequestToDomainMapper.toDomain(req))
                .map(BillingPolicyResponse::from);
    }

    @GetMapping("/{policyId}")
    @Operation(summary = "Get a billing policy by ID")
    public Mono<BillingPolicyResponse> getById(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID policyId) {
        return policyUseCase.findById(policyId, currentUser.tenantId()).map(BillingPolicyResponse::from);
    }

    @GetMapping
    @Operation(summary = "List all policies for a tenant")
    public Flux<BillingPolicyResponse> listByTenant(@RequestParam UUID tenantId) {
        return policyUseCase.findByTenantId(tenantId).map(BillingPolicyResponse::from);
    }

    @GetMapping("/active")
    @Operation(summary = "List active policies for a tenant")
    public Flux<BillingPolicyResponse> listActive(@RequestParam UUID tenantId) {
        return policyUseCase.findActiveByTenantId(tenantId).map(BillingPolicyResponse::from);
    }

    @GetMapping("/default")
    @Operation(summary = "Get the default policy for a tenant")
    public Mono<BillingPolicyResponse> getDefault(@RequestParam UUID tenantId) {
        return policyUseCase.findDefaultForTenant(tenantId).map(BillingPolicyResponse::from);
    }

    @PatchMapping("/{policyId}/activate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Activate a billing policy")
    public Mono<BillingPolicyResponse> activate(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID policyId) {
        return policyUseCase.activatePolicy(policyId, currentUser.tenantId()).map(BillingPolicyResponse::from);
    }

    @PatchMapping("/{policyId}/deactivate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Deactivate a billing policy")
    public Mono<BillingPolicyResponse> deactivate(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID policyId) {
        return policyUseCase.deactivatePolicy(policyId, currentUser.tenantId()).map(BillingPolicyResponse::from);
    }

    @PatchMapping("/{policyId}/archive")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Archive a billing policy")
    public Mono<BillingPolicyResponse> archive(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID policyId) {
        return policyUseCase.archivePolicy(policyId, currentUser.tenantId()).map(BillingPolicyResponse::from);
    }

    @DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a billing policy")
    public Mono<Void> delete(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID policyId) {
        return policyUseCase.deletePolicy(policyId, currentUser.tenantId());
    }

    // Multi-owner endpoints ──────────────────────────────────────────

    /**
     *  — Assigns a billing policy to a specific owner actor (FreelancerOrg, HubPoint…).
     */
    @PatchMapping("/{policyId}/assign-org")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = " — Assign a billing policy to an owner actor",
               description = "Links the policy to a FreelancerOrganization, HubPoint or "
                       + "Link network actor and sets the appropriate DSL access level.")
    public Mono<BillingPolicyResponse> assignToOrg(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID policyId,
            @RequestParam @NotBlank String orgId,
            @RequestParam @NotNull PolicyOwnerType ownerType) {
        return policyUseCase.assignPolicyToOrg(policyId, orgId, ownerType, currentUser.tenantId())
                .map(BillingPolicyResponse::from);
    }

    /**
     *  — Lists all policies owned by the given actor.
     * Used by FreelancerOrg, HubPoint, and Link dashboards.
     */
    @GetMapping("/owner/{ownerActorId}")
    @Operation(summary = " — List billing policies for a given owner actor")
    public Flux<BillingPolicyResponse> listByOwner(@PathVariable String ownerActorId) {
        return policyUseCase.findByOwnerActorId(ownerActorId).map(BillingPolicyResponse::from);
    }

    /**
     *  — Creates a billing policy from a named policy template.
     * The template provides seed rules appropriate for the owner type.
     */
    @PostMapping("/from-template")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = " — Create a billing policy from a template",
               description = "Instantiates a new billing policy using a named template. "
                       + "Templates provide default rules for FREELANCER_STANDARD, "
                       + "HUB_STORAGE_BASIC, LINK_NETWORK_STANDARD, etc.")
    public Mono<BillingPolicyResponse> createFromTemplate(
            @RequestParam @NotBlank String templateCode,
            @RequestParam @NotNull PolicyOwnerType ownerType,
            @RequestParam @NotBlank String ownerActorId,
            @RequestParam @NotNull UUID tenantId,
            @RequestParam(required = false, defaultValue = "XAF") String currency) {
        return policyUseCase.createPolicyFromTemplate(
                templateCode, ownerType, ownerActorId, tenantId,
                Map.of("currency", currency))
                .map(BillingPolicyResponse::from);
    }
}
