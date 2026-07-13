package com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.onboarding.application.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Candidate self-service onboarding — port of tnt-agency {@code OnboardingController}. */
@Tag(name = "Agency ERP Onboarding", description = "Agency registration applications (candidate)")
@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/onboarding/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit agency registration application")
    public Mono<ApiResponse<SubmitOnboardingResponse>> submit(
            @PathVariable UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody SubmitOnboardingRequest body) {
        return onboardingService.submit(new OnboardingService.SubmitInput(
                tenantId, userId,
                body.agencyName(), body.legalName(), body.agencyCode(), body.agencyType(),
                body.registrationNumber(),
                body.address() != null ? body.address().street() : null,
                null, null,
                body.address() != null ? body.address().city() : null,
                null,
                body.address() != null ? body.address().country() : null,
                null, null, null,
                body.contactEmail(), body.contactPhone(), body.website(),
                body.ownerName(), body.ownerEmail(), body.ownerPhone(),
                body.ownerNationalId(), body.ownerIdType(),
                body.docCniKey(), body.docRccmKey(), body.docProofKey(),
                body.autoAssign(), body.allowFreelancers(),
                body.hubRetentionHours(), body.maxFreelancers()
        )).map(r -> ApiResponse.success(new SubmitOnboardingResponse(
                r.agencyId(), r.applicationId(), r.agencyStatus(),
                r.kernelBusinessActorId(), r.kernelIdentityReady())));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/onboarding/applications/{agencyId}/kernel-identity")
    @Operation(summary = "Persist Kernel phase-1 business actor (BFF calls Kernel first)")
    public Mono<ApiResponse<KernelIdentityResponse>> linkKernelIdentity(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody LinkKernelIdentityRequest body) {
        return onboardingService.linkKernelIdentity(new OnboardingService.LinkKernelIdentityInput(
                tenantId, userId, agencyId, body.kernelBusinessActorId()
        )).map(r -> ApiResponse.success(new KernelIdentityResponse(
                r.agencyId(), r.applicationId(), r.kernelBusinessActorId(), r.readyForAdminApproval())));
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/onboarding/applications/me")
    @Operation(summary = "Get onboarding status for the authenticated applicant")
    public Mono<ApiResponse<MyOnboardingResponse>> myApplication(
            @PathVariable UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId) {
        return onboardingService.getForApplicant(tenantId, userId)
                .map(s -> ApiResponse.success(new MyOnboardingResponse(
                        s.applicationId(), s.agencyId(), s.agencyName(),
                        s.applicationStatus(), s.agencyStatus(),
                        s.kernelBusinessActorId(), s.kernelIdentityReady())));
    }

    record SubmitOnboardingRequest(
            @NotBlank String agencyName,
            @NotBlank String legalName,
            @NotBlank String agencyCode,
            @NotBlank String agencyType,
            @NotBlank String registrationNumber,
            AddressRequest address,
            @Email @NotBlank String contactEmail,
            @NotBlank String contactPhone,
            String website,
            @NotBlank String ownerName,
            @Email @NotBlank String ownerEmail,
            @NotBlank String ownerPhone,
            String ownerNationalId,
            String ownerIdType,
            String docCniKey,
            String docRccmKey,
            String docProofKey,
            boolean autoAssign,
            boolean allowFreelancers,
            int hubRetentionHours,
            int maxFreelancers) {
        record AddressRequest(String street, String city, String country) {}
    }

    record LinkKernelIdentityRequest(@NotNull UUID kernelBusinessActorId) {}

    record SubmitOnboardingResponse(
            UUID agencyId, UUID applicationId, String agencyStatus,
            UUID kernelBusinessActorId, boolean kernelIdentityReady) {}

    record KernelIdentityResponse(
            UUID agencyId, UUID applicationId,
            UUID kernelBusinessActorId, boolean readyForAdminApproval) {}

    record MyOnboardingResponse(
            UUID applicationId, UUID agencyId, String agencyName,
            String applicationStatus, String agencyStatus,
            UUID kernelBusinessActorId, boolean kernelIdentityReady) {}
}
