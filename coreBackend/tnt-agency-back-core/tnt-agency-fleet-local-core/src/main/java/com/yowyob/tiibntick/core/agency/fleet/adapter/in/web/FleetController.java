package com.yowyob.tiibntick.core.agency.fleet.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.fleet.adapter.in.web.dto.FleetManLinkResponse;
import com.yowyob.tiibntick.core.agency.fleet.adapter.in.web.dto.VehicleResponse;
import com.yowyob.tiibntick.core.agency.fleet.application.service.FleetManLinkService;
import com.yowyob.tiibntick.core.agency.fleet.application.service.FleetService;
import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleSource;
import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/** Port of tnt-agency {@code FleetController}. */
@Tag(name = "Agency ERP Fleet", description = "Agency-local vehicle fleet lifecycle")
@RestController
@RequiredArgsConstructor
public class FleetController {

    private final FleetService fleetService;
    private final FleetManLinkService fleetManLinkService;

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/vehicles")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a vehicle to the agency fleet")
    public Mono<ApiResponse<VehicleResponse>> addVehicle(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody AddVehicleRequest body) {
        VehicleSource source = body.source() != null ? body.source() : VehicleSource.AGENCY;
        return fleetService.add(new FleetService.AddInput(
                tenantId, agencyId, body.branchId(),
                body.licensePlate(), body.brand(), body.model(),
                body.year(), body.vehicleType(), body.coreVehicleId(),
                source, body.fleetmanVehicleId()
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/assign")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Assign vehicle to a deliverer")
    public Mono<ApiResponse<VehicleResponse>> assignVehicle(
            @PathVariable UUID tenantId,
            @PathVariable UUID vehicleId,
            @RequestBody AssignVehicleRequest body) {
        return fleetService.assign(tenantId, vehicleId, body.delivererId()).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/unassign")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Unassign vehicle from deliverer")
    public Mono<ApiResponse<VehicleResponse>> unassignVehicle(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.unassign(tenantId, vehicleId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/maintenance")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send vehicle to maintenance")
    public Mono<ApiResponse<VehicleResponse>> sendToMaintenance(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.sendToMaintenance(tenantId, vehicleId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/maintenance/return")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Return vehicle from maintenance")
    public Mono<ApiResponse<VehicleResponse>> returnFromMaintenance(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.returnFromMaintenance(tenantId, vehicleId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/retire")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Retire vehicle from fleet")
    public Mono<ApiResponse<VehicleResponse>> retireVehicle(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.retire(tenantId, vehicleId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/core-link")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Link tnt-resource-core vehicle id (auto-synced on add when omitted)")
    public Mono<ApiResponse<VehicleResponse>> linkCoreVehicle(
            @PathVariable UUID tenantId,
            @PathVariable UUID vehicleId,
            @RequestBody LinkCoreVehicleRequest body) {
        return fleetService.linkCoreVehicle(tenantId, vehicleId, body.coreVehicleId())
                .map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/fleetman-link")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Link FleetMan vehicle id and provenance")
    public Mono<ApiResponse<VehicleResponse>> linkFleetMan(
            @PathVariable UUID tenantId,
            @PathVariable UUID vehicleId,
            @RequestBody LinkFleetManRequest body) {
        return fleetService.linkFleetMan(tenantId, vehicleId, body.fleetmanVehicleId(), body.source())
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}")
    @Operation(summary = "Get vehicle by id")
    public Mono<ApiResponse<VehicleResponse>> getVehicle(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.getById(tenantId, vehicleId).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/vehicles")
    @Operation(summary = "List fleet for an agency")
    public Mono<ApiResponse<List<VehicleResponse>>> getFleetByAgency(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return fleetService.listByAgency(tenantId, agencyId).collectList().map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/fleetman-link")
    @Operation(summary = "Get FleetMan link for agency (404 if not connected)")
    public Mono<ApiResponse<FleetManLinkResponse>> getFleetManLink(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return fleetManLinkService.get(tenantId, agencyId).map(ApiResponse::success);
    }

    @PutMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/fleetman-link")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create or update FleetMan link for agency")
    public Mono<ApiResponse<FleetManLinkResponse>> upsertFleetManLink(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody UpsertFleetManLinkRequest body) {
        return fleetManLinkService.upsert(tenantId, agencyId, new FleetManLinkService.UpsertInput(
                body.fleetmanUserId(), body.fleetmanFleetId(), body.email(),
                body.refreshTokenEnc(), body.status()
        )).map(ApiResponse::success);
    }

    record AddVehicleRequest(
            UUID branchId, String licensePlate, String brand, String model,
            int year, VehicleType vehicleType, UUID coreVehicleId,
            VehicleSource source, String fleetmanVehicleId) {}

    record AssignVehicleRequest(UUID delivererId) {}

    record LinkCoreVehicleRequest(@NotNull UUID coreVehicleId) {}

    record LinkFleetManRequest(@NotBlank String fleetmanVehicleId, VehicleSource source) {}

    record UpsertFleetManLinkRequest(
            String fleetmanUserId,
            @NotBlank String fleetmanFleetId,
            @NotBlank String email,
            String refreshTokenEnc,
            String status) {}
}
