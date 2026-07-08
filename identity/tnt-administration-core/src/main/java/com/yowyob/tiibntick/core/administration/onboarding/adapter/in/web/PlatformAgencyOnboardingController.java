package com.yowyob.tiibntick.core.administration.onboarding.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.administration.onboarding.application.port.in.AgencyOnboardingUseCase;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AgencyKernelIdentityRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AgencyKernelIdentityResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ApproveAgencyOnboardingRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ApproveAgencyOnboardingResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AssignAgencyOwnerRoleRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AssignAgencyOwnerRoleResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ProvisionAgencyOrganizationRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ProvisionAgencyOrganizationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Agency onboarding orchestration for platform backends — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} Bloc C. Registered public (platform
 * {@code X-Client-Id}/{@code X-Api-Key} required, see {@code TntAuthGatewaySecurityConfig});
 * the acting user's own Kernel-issued Bearer token (candidate for {@code kernel-identity},
 * admin for the rest) is forwarded to the Kernel unchanged.
 *
 * <p>{@code agencyRef} is a platform-side correlation identifier (e.g. {@code tnt-agency}'s
 * own onboarding-application UUID) — TiiBnTick Core does not persist the onboarding
 * request itself, only orchestrates the Kernel + TiiBnTick side effects (see
 * {@code AgencyOnboardingOrchestratorService}).
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/onboarding/agency/applications/{agencyRef}")
@Tag(name = "Platform Onboarding Gateway", description = "Agency onboarding orchestration for platform backends")
public class PlatformAgencyOnboardingController {

    private final AgencyOnboardingUseCase useCase;

    public PlatformAgencyOnboardingController(AgencyOnboardingUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/kernel-identity")
    @Operation(summary = "Phase 1 — create the candidate's Kernel BusinessActor (requires the candidate's own Bearer token)")
    public Mono<ResponseEntity<ApiResponse<AgencyKernelIdentityResponse>>> createKernelIdentity(
            @PathVariable String agencyRef,
            @RequestBody AgencyKernelIdentityRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return useCase.createKernelIdentity(request, authorization)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result)));
    }

    @PostMapping("/provision")
    @Operation(summary = "Phase 2 — create + approve the Kernel Organization, subscribe commercial plan/services (requires the admin's Bearer token)")
    public Mono<ResponseEntity<ApiResponse<ProvisionAgencyOrganizationResponse>>> provision(
            @PathVariable String agencyRef,
            @RequestBody ProvisionAgencyOrganizationRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return useCase.provisionOrganization(request, authorization)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/assign-owner-role")
    @Operation(summary = "Phase 2b — assign the owner's Kernel administration role (requires the admin's Bearer token)")
    public Mono<ResponseEntity<ApiResponse<AssignAgencyOwnerRoleResponse>>> assignOwnerRole(
            @PathVariable String agencyRef,
            @RequestBody AssignAgencyOwnerRoleRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return useCase.assignOwnerRole(request, authorization)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/approve")
    @Operation(summary = "Recommended one-call orchestration: provision + TiiBnTick Agency + role/settings bootstrap + owner role assignment (requires the admin's Bearer token)")
    public Mono<ResponseEntity<ApiResponse<ApproveAgencyOnboardingResponse>>> approve(
            @PathVariable String agencyRef,
            @RequestBody ApproveAgencyOnboardingRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return useCase.approve(request, authorization)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result)));
    }
}
