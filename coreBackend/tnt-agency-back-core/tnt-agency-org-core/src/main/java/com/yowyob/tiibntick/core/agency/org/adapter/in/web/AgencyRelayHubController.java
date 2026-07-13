package com.yowyob.tiibntick.core.agency.org.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRelayHubResponse;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRelayHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Relay hub configuration and hub-ops (parcel index → inventory-core).
 */
@Tag(name = "Agency ERP Relay Hubs", description = "Agency relay hub configuration")
@RestController
@RequiredArgsConstructor
public class AgencyRelayHubController {

    private final AgencyRelayHubService hubService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/hubs")
    @Operation(summary = "List relay hubs for an agency")
    public Mono<ApiResponse<List<AgencyRelayHubResponse>>> list(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return hubService.listByAgency(tenantId, agencyId)
                .collectList()
                .map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/hubs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a relay hub")
    public Mono<ApiResponse<AgencyRelayHubResponse>> create(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody CreateHubRequest req) {
        return hubService.create(new AgencyRelayHubService.CreateHubInput(
                tenantId, agencyId, req.branchId(),
                req.name(), req.code(),
                req.addrCity(), req.addrCountry(), req.addrStreet(), req.addrQuarter(),
                req.latitude(), req.longitude(),
                req.capacityUnits(), req.retentionDelayHours(), req.openingHours()
        )).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}")
    @Operation(summary = "Get relay hub by ID")
    public Mono<ApiResponse<AgencyRelayHubResponse>> getById(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId) {
        return hubService.getById(tenantId, hubId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}")
    @Operation(summary = "Configure a relay hub")
    public Mono<ApiResponse<AgencyRelayHubResponse>> configure(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestBody ConfigureHubRequest req) {
        return hubService.configure(new AgencyRelayHubService.ConfigureHubInput(
                tenantId, hubId, req.name(), req.capacityUnits(),
                req.retentionDelayHours(), req.openingHours()
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/branch")
    @Operation(summary = "Attach hub to a branch")
    public Mono<ApiResponse<AgencyRelayHubResponse>> attachToBranch(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestBody AttachBranchRequest req) {
        return hubService.attachToBranch(tenantId, hubId, req.branchId()).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/status")
    @Operation(summary = "Open or close a hub")
    public Mono<ApiResponse<AgencyRelayHubResponse>> changeStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestBody ChangeStatusRequest req) {
        if ("OPEN".equalsIgnoreCase(req.status())) {
            return hubService.open(tenantId, hubId).map(ApiResponse::success);
        }
        return hubService.close(tenantId, hubId).map(ApiResponse::success);
    }

    public record CreateHubRequest(
            UUID branchId,
            @NotBlank String name,
            @NotBlank String code,
            @NotBlank String addrCity,
            @NotBlank String addrCountry,
            String addrStreet,
            String addrQuarter,
            Double latitude,
            Double longitude,
            int capacityUnits,
            int retentionDelayHours,
            String openingHours) {}

    public record ConfigureHubRequest(
            String name, Integer capacityUnits, Integer retentionDelayHours, String openingHours) {}

    public record AttachBranchRequest(UUID branchId) {}

    public record ChangeStatusRequest(String status) {}
}
