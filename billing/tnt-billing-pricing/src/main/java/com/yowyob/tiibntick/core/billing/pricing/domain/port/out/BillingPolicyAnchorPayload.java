package com.yowyob.tiibntick.core.billing.pricing.domain.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Billing-pricing-owned payload for {@link BillingPolicyAnchorPort#anchor}.
 *
 * <p>Deliberately independent from any {@code tnt-trust-core} domain type — the
 * implementing adapter (in {@code tnt-trust-core}) maps this into its own
 * {@code BillingPolicyRecord}, keeping the hexagonal boundary between the two modules.
 *
 * @param policyId          the activated billing policy identifier
 * @param ownerActorId      the policy owner's actor id (agency, FreelancerOrg, hub point, …)
 * @param policySummaryJson JSON summary of the activated pricing rules (metadata only —
 *                          the full DSL policy stays in {@code tnt-billing-pricing})
 * @author MANFOUO Braun
 */
public record BillingPolicyAnchorPayload(
        UUID tenantId,
        UUID policyId,
        String ownerActorId,
        String policySummaryJson,
        Instant activatedAt) {
}
