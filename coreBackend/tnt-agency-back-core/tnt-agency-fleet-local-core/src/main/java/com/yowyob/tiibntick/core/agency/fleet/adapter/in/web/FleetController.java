package com.yowyob.tiibntick.core.agency.fleet.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.fleet.adapter.in.web.dto.VehicleResponse;
import com.yowyob.tiibntick.core.agency.fleet.application.service.FleetService;
import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Port of tnt-agency {@code FleetController}. */
@Tag(name = "Agency ERP Fleet", description = "Agency-local vehicle fleet lifecycle")
@RestController
@RequiredArgsConstructor
public class FleetController {

    private final FleetService fleetService;

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a vehicle to the agency fleet")
    public Mono<ApiResponse<VehicleResponse>> addVehicle(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody AddVehicleRequest body) {
        return fleetService.add(new FleetService.AddInput(
                tenantId, agencyId, body.branchId(),
                body.licensePlate(), body.brand(), body.model(),
                body.year(), body.vehicleType(), body.coreVehicleId()
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/assign")
    @Operation(summary = "Assign vehicle to a deliverer")
    public Mono<ApiResponse<VehicleResponse>> assignVehicle(
            @PathVariable UUID tenantId,
            @PathVariable UUID vehicleId,
            @RequestBody AssignVehicleRequest body) {
        return fleetService.assign(tenantId, vehicleId, body.delivererId()).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/unassign")
    @Operation(summary = "Unassign vehicle from deliverer")
    public Mono<ApiResponse<VehicleResponse>> unassignVehicle(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.unassign(tenantId, vehicleId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/maintenance")
    @Operation(summary = "Send vehicle to maintenance")
    public Mono<ApiResponse<VehicleResponse>> sendToMaintenance(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.sendToMaintenance(tenantId, vehicleId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/maintenance/return")
    @Operation(summary = "Return vehicle from maintenance")
    public Mono<ApiResponse<VehicleResponse>> returnFromMaintenance(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.returnFromMaintenance(tenantId, vehicleId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/retire")
    @Operation(summary = "Retire vehicle from fleet")
    public Mono<ApiResponse<VehicleResponse>> retireVehicle(
            @PathVariable UUID tenantId, @PathVariable UUID vehicleId) {
        return fleetService.retire(tenantId, vehicleId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/vehicles/{vehicleId}/core-link")
    @Operation(summary = "Link tnt-resource-core vehicle id (auto-synced on add when omitted)")
    public Mono<ApiResponse<VehicleResponse>> linkCoreVehicle(
            @PathVariable UUID tenantId,
            @PathVariable UUID vehicleId,
            @RequestBody LinkCoreVehicleRequest body) {
        return fleetService.linkCoreVehicle(tenantId, vehicleId, body.coreVehicleId())
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

    record AddVehicleRequest(
            UUID branchId, String licensePlate, String brand, String model,
            int year, VehicleType vehicleType, UUID coreVehicleId) {}

    record AssignVehicleRequest(UUID delivererId) {}

    record LinkCoreVehicleRequest(@NotNull UUID coreVehicleId) {}
}
