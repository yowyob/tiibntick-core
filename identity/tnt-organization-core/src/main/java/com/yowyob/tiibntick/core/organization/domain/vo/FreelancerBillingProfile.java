package com.yowyob.tiibntick.core.organization.domain.vo;

import java.util.UUID;

/**
 * Value Object holding the billing configuration of a FreelancerOrganization.
 *
 * <p>Links the freelancer to an active {@code BillingPolicy} (managed by
 * {@code tnt-billing-pricing}) and stores VAT/tax declarations relevant for
 * Cameroonian tax compliance.
 *
 * @param activePolicyId      UUID of the currently active BillingPolicy (nullable — no policy yet)
 * @param defaultTemplateCode Code of the template used to generate the active policy (nullable)
 * @param vatApplicable       Whether the freelancer is VAT-registered (default: false)
 * @param taxId               NUI (Numéro d'Identifiant Unique) Cameroun or equivalent (nullable)
 *
 * @author MANFOUO Braun
 */
public record FreelancerBillingProfile(
        UUID activePolicyId,
        String defaultTemplateCode,
        boolean vatApplicable,
        String taxId
) {

    /**
     * Creates a blank billing profile for a newly registered freelancer.
     * No policy, no VAT, no tax ID yet.
     *
     * @return an empty {@link FreelancerBillingProfile}
     */
    public static FreelancerBillingProfile empty() {
        return new FreelancerBillingProfile(null, null, false, null);
    }

    /**
     * Returns a copy of this profile with the given active policy ID.
     *
     * @param policyId the UUID of the new active billing policy
     * @return updated profile
     */
    public FreelancerBillingProfile withActivePolicy(UUID policyId) {
        return new FreelancerBillingProfile(policyId, defaultTemplateCode, vatApplicable, taxId);
    }

    /**
     * Returns a copy of this profile with VAT registration details.
     *
     * @param taxId the NUI or equivalent tax identifier
     * @return updated profile with vatApplicable = true
     */
    public FreelancerBillingProfile withVatRegistration(String taxId) {
        return new FreelancerBillingProfile(activePolicyId, defaultTemplateCode, true, taxId);
    }
}
