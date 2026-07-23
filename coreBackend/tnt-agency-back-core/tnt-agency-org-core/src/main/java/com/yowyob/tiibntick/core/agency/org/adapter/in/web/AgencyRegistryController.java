package com.yowyob.tiibntick.core.agency.org.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencySettingsResponse;
import com.yowyob.tiibntick.core.agency.org.application.mapper.AgencyOrgMapper;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Agency ERP registry — legal entities and settings in schema {@code agency_org}.
 *
 * <p>Distinct from {@code tnt-organization-core} ({@code /api/v1/tenants/{tenantId}/agencies}).
 */
@Tag(name = "Agency ERP Registry", description = "Agency legal entities, profile and settings")
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies")
@RequiredArgsConstructor
public class AgencyRegistryController {

    private final AgencyRegistryService registryService;

    @GetMapping
    @Operation(summary = "List agencies for tenant")
    public Mono<ApiResponse<List<AgencyRegistryResponse>>> list(@PathVariable UUID tenantId) {
        return registryService.listByTenant(tenantId)
                .map(AgencyOrgMapper::toAgencyResponse)
                .collectList()
                .map(ApiResponse::success);
    }

    @GetMapping("/{agencyId}")
    @Operation(summary = "Get agency by ID")
    public Mono<ApiResponse<AgencyRegistryResponse>> getById(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return registryService.getById(tenantId, agencyId)
                .map(AgencyOrgMapper::toAgencyResponse)
                .map(ApiResponse::success);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new agency")
    public Mono<ApiResponse<AgencyRegistryResponse>> register(
            @PathVariable UUID tenantId,
            @Valid @RequestBody RegisterAgencyRequest req) {
        return registryService.register(new AgencyRegistryService.RegisterAgencyInput(
                tenantId, req.name(), req.agencyCode(), req.type(), req.registrationNumber(),
                req.address() != null ? req.address().street() : null,
                req.address() != null ? req.address().landmark() : null,
                req.address() != null ? req.address().quarter() : null,
                req.address() != null ? req.address().city() : null,
                req.address() != null ? req.address().region() : null,
                req.address() != null ? req.address().country() : null,
                req.address() != null ? req.address().postalCode() : null,
                req.address() != null ? req.address().lat() : null,
                req.address() != null ? req.address().lon() : null,
                req.contactEmail(), req.contactPhone(), req.logoUrl(), req.website()
        )).map(AgencyOrgMapper::toAgencyResponse).map(ApiResponse::success);
    }

    @PostMapping("/{agencyId}/sync-platform-core")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Register agency on platform organization core")
    public Mono<ApiResponse<AgencyRegistryResponse>> syncPlatformCore(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return registryService.syncPlatformCore(tenantId, agencyId)
                .map(AgencyOrgMapper::toAgencyResponse)
                .map(ApiResponse::success);
    }

    @PatchMapping("/{agencyId}/activate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Activate agency")
    public Mono<ApiResponse<AgencyRegistryResponse>> activate(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return registryService.activate(tenantId, agencyId)
                .map(AgencyOrgMapper::toAgencyResponse)
                .map(ApiResponse::success);
    }

    @PatchMapping("/{agencyId}/suspend")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Suspend agency")
    public Mono<ApiResponse<AgencyRegistryResponse>> suspend(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestParam String reason) {
        return registryService.suspend(tenantId, agencyId, reason)
                .map(AgencyOrgMapper::toAgencyResponse)
                .map(ApiResponse::success);
    }

    @PatchMapping("/{agencyId}/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update agency profile")
    public Mono<ApiResponse<AgencyRegistryResponse>> updateProfile(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @Valid @RequestBody UpdateAgencyProfileRequest req) {
        return registryService.updateProfile(new AgencyRegistryService.UpdateProfileInput(
                tenantId, agencyId, req.name(), req.registrationNumber(),
                req.address() != null ? req.address().street() : null,
                req.address() != null ? req.address().landmark() : null,
                req.address() != null ? req.address().quarter() : null,
                req.address() != null ? req.address().city() : null,
                req.address() != null ? req.address().region() : null,
                req.address() != null ? req.address().country() : null,
                req.address() != null ? req.address().postalCode() : null,
                req.address() != null ? req.address().lat() : null,
                req.address() != null ? req.address().lon() : null,
                req.email(), req.phone(), req.logoUrl(), req.website()
        )).map(AgencyOrgMapper::toAgencyResponse).map(ApiResponse::success);
    }

    @GetMapping("/{agencyId}/settings")
    @Operation(summary = "Get agency settings")
    public Mono<ApiResponse<AgencySettingsResponse>> getSettings(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return registryService.getSettings(tenantId, agencyId)
                .map(AgencyOrgMapper::toSettingsResponse)
                .map(ApiResponse::success);
    }

    @PatchMapping("/{agencyId}/settings")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update agency settings")
    public Mono<ApiResponse<AgencySettingsResponse>> updateSettings(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @Valid @RequestBody UpdateAgencySettingsRequest req) {
        return registryService.updateSettings(new AgencyRegistryService.UpdateSettingsInput(
                tenantId, agencyId,
                req.autoAssignMissions(), req.allowFreelancerAssociation(),
                req.hubRetentionDelayHours(), req.defaultCommissionRate(),
                req.maxActiveBranches(), req.timezone()
        )).map(AgencyOrgMapper::toSettingsResponse).map(ApiResponse::success);
    }

    public record RegisterAgencyRequest(
            @NotBlank String name,
            @NotBlank String agencyCode,
            @NotBlank String type,
            @NotBlank String registrationNumber,
            AddressRequest address,
            @NotBlank @Email String contactEmail,
            @NotBlank String contactPhone,
            String logoUrl,
            String website) {}

    public record UpdateAgencyProfileRequest(
            String name,
            String registrationNumber,
            AddressRequest address,
            @Email String email,
            String phone,
            String logoUrl,
            String website) {}

    public record UpdateAgencySettingsRequest(
            Boolean autoAssignMissions,
            Boolean allowFreelancerAssociation,
            Integer hubRetentionDelayHours,
            BigDecimal defaultCommissionRate,
            Integer maxActiveBranches,
            String timezone) {}

    public record AddressRequest(
            String street, String landmark, String quarter,
            @NotBlank String city, String region,
            @NotBlank String country, String postalCode,
            Double lat, Double lon) {}
}
