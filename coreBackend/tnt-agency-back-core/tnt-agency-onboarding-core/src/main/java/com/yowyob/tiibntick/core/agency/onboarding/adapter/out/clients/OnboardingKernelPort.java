package com.yowyob.tiibntick.core.agency.onboarding.adapter.out.clients;

import com.yowyob.tiibntick.core.agency.onboarding.domain.OnboardingApplication;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — agency onboarding via the TiiBnTick Core gateway
 * ({@code /api/v1/onboarding/agency/**}).
 */
public interface OnboardingKernelPort {

    /** Admin provisioning input (phase 2). */
    record ProvisionRequest(
            AgencyRegistryResponse agency,
            OnboardingApplication application,
            String defaultCurrency,
            UUID reviewerId
    ) {}

    /** Identifiers and side effects returned by Core after approval. */
    record ProvisionResult(
            UUID organizationId,
            UUID businessActorId,
            UUID coreAgencyId,
            boolean tntRolesProvisioned,
            boolean platformOptionsInitialized,
            UUID ownerRoleAssignmentId
    ) {}

    /**
     * Phase 2 — full orchestration via Core {@code POST .../approve}.
     */
    Mono<ProvisionResult> provisionOrganization(ProvisionRequest request);

    /**
     * Phase 2b — assigns the owner role if the {@code /approve} call did not do so.
     */
    Mono<Void> assignAgencyManagerRole(UUID agencyId, UUID tenantId, UUID organizationId,
                                       UUID ownerUserId, UUID reviewerId);
}
