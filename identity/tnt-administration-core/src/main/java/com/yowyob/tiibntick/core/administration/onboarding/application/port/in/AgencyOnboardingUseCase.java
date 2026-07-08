package com.yowyob.tiibntick.core.administration.onboarding.application.port.in;

import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AgencyKernelIdentityRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AgencyKernelIdentityResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ApproveAgencyOnboardingRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ApproveAgencyOnboardingResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AssignAgencyOwnerRoleRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AssignAgencyOwnerRoleResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ProvisionAgencyOrganizationRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ProvisionAgencyOrganizationResponse;
import reactor.core.publisher.Mono;

/**
 * Primary (inbound) use-case: orchestrates agency onboarding for platform backends —
 * Kernel {@code BusinessActor}/{@code Organization} provisioning + TiiBnTick {@code Agency}
 * creation + role/permission bootstrapping — behind a single Core API, so platforms never
 * call the Kernel or coordinate multiple TiiBnTick modules themselves.
 * See {@code CORE_KERNEL_GATEWAY_SPEC.md} Bloc C.
 *
 * <p>Implemented by {@code AgencyOnboardingOrchestratorService}, exposed by
 * {@code PlatformAgencyOnboardingController}.
 *
 * @author MANFOUO Braun
 */
public interface AgencyOnboardingUseCase {

    /** Phase 1 — creates the candidate's Kernel {@code BusinessActor}. Requires the candidate's own Bearer token. */
    Mono<AgencyKernelIdentityResponse> createKernelIdentity(AgencyKernelIdentityRequest request, String candidateBearerAuthorization);

    /** Phase 2 — creates + approves the Kernel {@code Organization} and subscribes services. Requires the admin's Bearer token. */
    Mono<ProvisionAgencyOrganizationResponse> provisionOrganization(ProvisionAgencyOrganizationRequest request, String adminBearerAuthorization);

    /** Phase 2b — assigns the owner's Kernel administration role. Requires the admin's Bearer token. */
    Mono<AssignAgencyOwnerRoleResponse> assignOwnerRole(AssignAgencyOwnerRoleRequest request, String adminBearerAuthorization);

    /** Recommended one-call orchestration: Phase 2 + TiiBnTick Agency creation + role/settings bootstrap + Phase 2b. */
    Mono<ApproveAgencyOnboardingResponse> approve(ApproveAgencyOnboardingRequest request, String adminBearerAuthorization);
}
