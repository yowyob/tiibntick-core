package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Freelancer rate grid, a facade over the billing layer: the grid is stored as an active
 * {@code BillingPolicy} owned by the freelancer and priced by the shared pricing engine.
 *
 * @author MANFOUO BRAUN
 */
public interface FreelancerPricingPolicyUseCase {

    /** The grid in force for this delivery person, or empty if none was ever published. */
    Mono<FreelancerPricingDTO> getPolicy(UUID freelancerId);

    /**
     * Same as {@link #upsertPolicy(UUID, FreelancerPricingDTO, UUID)} without a tenant: only an
     * already-existing billing policy can be updated, otherwise the grid is kept in the legacy
     * table until a tenant-scoped call creates the policy.
     */
    default Mono<FreelancerPricingDTO> upsertPolicy(UUID freelancerId, FreelancerPricingDTO request) {
        return upsertPolicy(freelancerId, request, null);
    }

    /**
     * Merges {@code request} into the current grid and persists it as the freelancer's active billing
     * policy in {@code tenantId}.
     */
    Mono<FreelancerPricingDTO> upsertPolicy(UUID freelancerId, FreelancerPricingDTO request, UUID tenantId);

    /**
     * Caller-checked variant used by the REST layer: unless {@code admin}, the caller must be the user
     * behind delivery person {@code freelancerId}. A refused caller gets an
     * {@link org.springframework.security.access.AccessDeniedException}.
     */
    Mono<FreelancerPricingDTO> upsertPolicyAsCaller(UUID freelancerId, FreelancerPricingDTO request,
                                                    UUID tenantId, UUID callerId, boolean admin);
}
