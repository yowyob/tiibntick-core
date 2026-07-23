package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto.HubOccupancyResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto.HubParcelResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubOccupancyService;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelExpiryService;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "Agency ERP Hub Operations", description = "Relay hub parcel deposit and withdrawal")
@RestController
@RequiredArgsConstructor
public class HubParcelController {

    private final HubParcelService hubParcelService;
    private final HubOccupancyService hubOccupancyService;
    private final HubParcelExpiryService hubParcelExpiryService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/parcels")
    @Operation(summary = "List parcels at a relay hub")
    public Mono<ApiResponse<List<HubParcelResponse>>> list(
            @PathVariable UUID tenantId, @PathVariable UUID hubId) {
        return hubParcelService.listByHub(tenantId, hubId).collectList().map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/parcels/deposit")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposit parcel at hub (orchestrates inventory-core)")
    public Mono<ApiResponse<HubParcelResponse>> deposit(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestBody DepositParcelRequest body) {
        return hubParcelService.deposit(new HubParcelService.DepositInput(
                tenantId, hubId, body.missionId(), body.trackingCode(), body.packageId()
        )).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/hub-parcels/{trackingCode}/withdraw")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Withdraw parcel from hub")
    public Mono<ApiResponse<HubParcelResponse>> withdraw(
            @PathVariable UUID tenantId,
            @PathVariable String trackingCode,
            @RequestBody WithdrawParcelRequest body) {
        return hubParcelService.withdraw(new HubParcelService.WithdrawInput(
                tenantId, trackingCode, body.withdrawnBy(), body.identityVerified(),
                body.pickedUpByActorId()
        )).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/occupancy")
    @Operation(summary = "Hub occupancy snapshot (optionally synced from inventory-core)")
    public Mono<ApiResponse<HubOccupancyResponse>> occupancy(
            @PathVariable UUID tenantId, @PathVariable UUID hubId) {
        return hubOccupancyService.getOccupancy(tenantId, hubId).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/expired/process")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Process expired hub parcels for tenant")
    public Mono<ApiResponse<ExpiredProcessResult>> processExpired(@PathVariable UUID tenantId) {
        return hubParcelExpiryService.processExpired(tenantId)
                .map(count -> new ExpiredProcessResult(count))
                .map(ApiResponse::success);
    }

    public record ExpiredProcessResult(int processedCount) {}

    public record DepositParcelRequest(UUID missionId, String trackingCode, UUID packageId) {}

    public record WithdrawParcelRequest(
            String withdrawnBy, boolean identityVerified, UUID pickedUpByActorId) {}
}
