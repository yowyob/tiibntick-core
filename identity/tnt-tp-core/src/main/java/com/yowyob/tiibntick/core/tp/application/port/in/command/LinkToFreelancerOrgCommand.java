package com.yowyob.tiibntick.core.tp.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to link a TntClientProfile to a FreelancerOrganization.
 *
 * <p>When a client becomes a regular customer of a FreelancerOrg,
 * the platform creates a providerLink between the client's TntClientProfile
 * and the FreelancerOrg's UUID, and grants the {@code FREELANCER_ORG_CLIENT} role.
 *
 * <p>This link enables:
 * <ul>
 *   <li>FreelancerOrg to query their client base ({@code findClientsByFreelancerOrg}).</li>
 *   <li>Billing DSL to evaluate {@code isRecurringClient} for loyalty discounts.</li>
 *   <li>Client to appear in the FreelancerOrg dashboard.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record LinkToFreelancerOrgCommand(
        /** The third party UUID (Kernel integration key). */
        @NotNull UUID thirdPartyId,
        /** The tenant context. */
        @NotNull UUID tenantId,
        /**
         * UUID of the FreelancerOrganization to link to.
         * References tnt-organization-core UUID — pure integration key.
         */
        @NotBlank String freelancerOrgId
) {}
