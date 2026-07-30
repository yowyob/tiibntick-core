package com.yowyob.tiibntick.core.agency.staff.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRelayHubResponse;
import com.yowyob.tiibntick.core.agency.staff.adapter.in.web.dto.StaffMemberResponse;
import com.yowyob.tiibntick.core.agency.staff.application.service.HubOperatorProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Tag(name = "Agency Hub Operators", description = "Provision gérant de hub with login credentials")
@RestController
@RequiredArgsConstructor
public class HubOperatorController {

    private final HubOperatorProvisioningService provisioningService;

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/hubs/{hubId}/operators")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create hub operator account, email credentials, link to hub")
    public Mono<ApiResponse<ProvisionOperatorResponse>> provision(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @PathVariable UUID hubId,
            @RequestBody ProvisionOperatorRequest body) {
        return provisioningService.provision(new HubOperatorProvisioningService.ProvisionOperatorInput(
                        tenantId, agencyId, hubId, body.branchId(),
                        body.fullName(), body.phone(), body.email()))
                .map(r -> ApiResponse.success(new ProvisionOperatorResponse(
                        r.staff(), r.hub(), r.operatorUserId())));
    }

    public record ProvisionOperatorRequest(
            @NotBlank String fullName,
            @NotBlank String phone,
            @NotBlank @Email String email,
            UUID branchId) {}

    public record ProvisionOperatorResponse(
            StaffMemberResponse staff,
            AgencyRelayHubResponse hub,
            @NotNull UUID operatorUserId) {}
}
