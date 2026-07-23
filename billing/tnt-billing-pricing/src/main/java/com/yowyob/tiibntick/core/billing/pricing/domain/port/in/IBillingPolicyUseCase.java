package com.yowyob.tiibntick.core.billing.pricing.domain.port.in;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.FleetCostParameters;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound port — BillingPolicy lifecycle and management.
 *
 * <h3> additions — FreelancerOrg and multi-owner support</h3>
 * <ul>
 *   <li>{@link #createPolicyFromTemplate} — creates a policy from a named template</li>
 *   <li>{@link #assignPolicyToFreelancerOrg} — links a policy to a FreelancerOrg actor</li>
 *   <li>{@link #findByOwnerActorId} — queries policies by owner actor ID</li>
 *   <li>{@link #computeOperationalCost} — estimates freight cost using fleet parameters</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface IBillingPolicyUseCase {

    Mono<BillingPolicy> createPolicy(BillingPolicy policy);

    /**
     * Updates an existing policy, scoped to the caller's tenant.
     *
     * <p>Audit n°7 · #5 (IDOR) — the existing policy is fetched with
     * {@code findByIdAndTenantId} so a caller cannot mutate another tenant's policy.
     *
     * @param policy   the updated policy fields (identified by {@code policy.getId()})
     * @param tenantId the tenant the policy must belong to
     */
    Mono<BillingPolicy> updatePolicy(BillingPolicy policy, UUID tenantId);

    /**
     * Activates a policy, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — tenant ownership is verified before mutation.
     */
    Mono<BillingPolicy> activatePolicy(UUID policyId, UUID tenantId);

    /**
     * Deactivates a policy, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — tenant ownership is verified before mutation.
     */
    Mono<BillingPolicy> deactivatePolicy(UUID policyId, UUID tenantId);

    /**
     * Archives a policy, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — tenant ownership is verified before mutation.
     */
    Mono<BillingPolicy> archivePolicy(UUID policyId, UUID tenantId);

    /**
     * Retrieves a policy by ID, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — never call without a tenant to check ownership.
     */
    Mono<BillingPolicy> findById(UUID policyId, UUID tenantId);

    Mono<BillingPolicy> findDefaultForTenant(UUID tenantId);

    Flux<BillingPolicy> findByTenantId(UUID tenantId);

    Flux<BillingPolicy> findActiveByTenantId(UUID tenantId);

    /**
     * Deletes a policy, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — tenant ownership is verified before deletion.
     */
    Mono<Void> deletePolicy(UUID policyId, UUID tenantId);

    // Multi-owner support ─────────────────────────────────────────────

    /**
     * Creates a new billing policy by instantiating a named template.
     *
     * <p>The template provides default rules which the owner can customize
     * within their {@link com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel}.
     *
     * @param templateCode  unique code of the source template (e.g. "FREELANCER_STANDARD")
     * @param ownerType     classification of the new policy owner
     * @param ownerActorId  UUID string of the owner actor (e.g. FreelancerOrg ID)
     * @param tenantId      tenant scope for the new policy
     * @param params        optional override parameters (e.g. base price, currency)
     * @return the new policy created from the template
     */
    Mono<BillingPolicy> createPolicyFromTemplate(String templateCode,
                                                   PolicyOwnerType ownerType,
                                                   String ownerActorId,
                                                   UUID tenantId,
                                                   Map<String, Object> params);

    /**
     * Assigns an existing policy to a FreelancerOrganization (or other actor).
     *
     * <p>Sets the {@code ownerType} and {@code ownerActorId} on the policy
     * and emits a {@link com.yowyob.tiibntick.core.billing.pricing.domain.event.BillingPolicyAssignedToOrg} event.
     *
     * @param policyId    UUID of the policy to assign
     * @param orgId       UUID string of the FreelancerOrg (or other actor)
     * @param ownerType   type of the owner actor
     * @param tenantId    the tenant the policy must belong to (Audit n°7 · #5 — IDOR fix)
     * @return the updated policy
     */
    Mono<BillingPolicy> assignPolicyToOrg(UUID policyId, String orgId, PolicyOwnerType ownerType, UUID tenantId);

    /**
     * Finds all policies owned by the given actor ID.
     *
     * @param ownerActorId UUID string of the owner actor
     * @return flux of matching policies
     */
    Flux<BillingPolicy> findByOwnerActorId(String ownerActorId);

    /**
     * Estimates the operational cost for a given delivery context using fleet parameters.
     *
     * <p>Uses the FleetCostParameters stored in the policy (if any) or the provided params.
     *
     * @param policyId          the billing policy to use
     * @param ctx               the pricing context
     * @param fleetParams       fleet cost parameters (null = use policy's stored params)
     * @return estimated operational cost in XAF
     */
    Mono<BigDecimal> computeOperationalCost(UUID policyId, PricingContext ctx,
                                             FleetCostParameters fleetParams);
}
