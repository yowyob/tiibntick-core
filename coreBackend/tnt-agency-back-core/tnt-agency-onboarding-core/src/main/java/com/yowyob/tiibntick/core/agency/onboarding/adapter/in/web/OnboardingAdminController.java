package com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto.OnboardingDetailResponse;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto.OnboardingListItemResponse;
import com.yowyob.tiibntick.core.agency.onboarding.application.service.OnboardingService;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Admin validation of onboarding requests — port of tnt-agency {@code OnboardingAdminController}. */
@Tag(name = "Agency ERP Onboarding Admin", description = "Platform admin validation of agency registrations")
@RestController
@RequiredArgsConstructor
public class OnboardingAdminController {

    private final OnboardingService onboardingService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/admin/onboarding/requests")
    @Operation(summary = "List pending onboarding requests")
    public Mono<ApiResponse<List<OnboardingListItemResponse>>> list(@PathVariable UUID tenantId) {
        return onboardingService.listPending(tenantId).collectList().map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/admin/onboarding/requests/{agencyId}")
    @Operation(summary = "Get onboarding request detail")
    public Mono<ApiResponse<OnboardingDetailResponse>> get(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return onboardingService.getByAgencyId(tenantId, agencyId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/admin/onboarding/requests/{agencyId}/approve")
    @Operation(summary = "Approve onboarding request (ERP: activate agency + register manager staff)")
    public Mono<ApiResponse<AgencyRegistryResponse>> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestHeader("X-User-Id") UUID reviewerId) {
        return onboardingService.approve(tenantId, agencyId, reviewerId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/admin/onboarding/requests/{agencyId}/reject")
    @Operation(summary = "Reject onboarding request")
    public Mono<ApiResponse<OnboardingListItemResponse>> reject(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestHeader("X-User-Id") UUID reviewerId,
            @Valid @RequestBody RejectRequest body) {
        return onboardingService.reject(tenantId, agencyId, reviewerId, body.reason())
                .map(ApiResponse::success);
    }

    record RejectRequest(@NotBlank String reason) {}
}
