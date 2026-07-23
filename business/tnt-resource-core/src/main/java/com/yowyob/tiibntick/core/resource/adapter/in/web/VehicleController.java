package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.resource.application.port.in.*;
import com.yowyob.tiibntick.core.resource.domain.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for fleet vehicle lifecycle management.
 * Path aligned with Kernel Core's resource management convention at {@code /api/resources/vehicles}.
 *
 * <p>Replaces the WebFlux functional router ({@code VehicleRouterConfig} + {@code VehicleHandler})
 * with annotation-driven routing for consistent Springdoc/OpenAPI discovery.
 *
 * <p>The tenant is always resolved from the authenticated {@code @CurrentUser} identity,
 * never from client-supplied input (query param or request body) — see
 * {@code EquipmentController} for the sibling pattern.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Vehicles", description = "Fleet vehicle lifecycle: registration, assignment, maintenance, retirement")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {

    private final CreateVehicleUseCase createVehicleUseCase;
    private final GetVehicleUseCase getVehicleUseCase;
    private final ListVehiclesByAgencyUseCase listVehiclesUseCase;
    private final AssignVehicleUseCase assignVehicleUseCase;
    private final UnassignVehicleUseCase unassignVehicleUseCase;
    private final SendVehicleToMaintenanceUseCase sendToMaintenanceUseCase;
    private final CompleteVehicleMaintenanceUseCase completeMaintenanceUseCase;
    private final RetireVehicleUseCase retireVehicleUseCase;
    private final UpdateVehicleOdometerUseCase updateOdometerUseCase;
    private final UpdateVehicleLocationUseCase updateLocationUseCase;
    private final FindBestVehicleForMissionUseCase findBestVehicleUseCase;
    private final CheckMaintenanceDueUseCase checkMaintenanceDueUseCase;
    private final ScheduleMaintenanceAlertUseCase scheduleMaintenanceAlertUseCase;

    @Operation(summary = "Register a new vehicle in the fleet")
    @PostMapping("/api/resources/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public Mono<Vehicle> createVehicle(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestBody CreateVehicleRequest body) {
        return createVehicleUseCase.createVehicle(new CreateVehicleCommand(
                currentUser.tenantId(), body.organizationId(), body.agencyId(),
                body.registrationNumber(), body.brand(), body.model(), body.yearOfManufacture(),
                VehicleType.valueOf(body.type()), body.maxWeightKg(), body.maxVolumeM3(),
                body.hasRefrigeration() != null && body.hasRefrigeration()));
    }

    @Operation(summary = "Get vehicle by ID")
    @GetMapping("/api/resources/vehicles/{vehicleId}")
    public Mono<Vehicle> getVehicle(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId) {
        return getVehicleUseCase.getVehicle(currentUser.tenantId(), vehicleId);
    }

    @Operation(summary = "List vehicles for an agency")
    @GetMapping("/api/resources/agencies/{agencyId}/vehicles")
    public Flux<Vehicle> listByAgency(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID agencyId,
            @RequestParam(required = false) String status) {
        VehicleStatus statusFilter = status != null ? VehicleStatus.valueOf(status) : null;
        return listVehiclesUseCase.listByAgency(currentUser.tenantId(), agencyId, statusFilter);
    }

    @Operation(summary = "Assign vehicle to a deliverer and mission")
    @PostMapping("/api/resources/vehicles/{vehicleId}/assign")
    @PreAuthorize("isAuthenticated()")
    public Mono<Vehicle> assignVehicle(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId,
            @RequestBody AssignVehicleRequest body) {
        return assignVehicleUseCase.assignVehicle(
                new AssignVehicleCommand(currentUser.tenantId(), vehicleId, body.delivererId(), body.missionId()));
    }

    @Operation(summary = "Unassign vehicle from its current deliverer")
    @PostMapping("/api/resources/vehicles/{vehicleId}/unassign")
    @PreAuthorize("isAuthenticated()")
    public Mono<Vehicle> unassignVehicle(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId) {
        return unassignVehicleUseCase.unassignVehicle(currentUser.tenantId(), vehicleId);
    }

    @Operation(summary = "Send vehicle to maintenance")
    @PostMapping("/api/resources/vehicles/{vehicleId}/maintenance")
    @PreAuthorize("isAuthenticated()")
    public Mono<Vehicle> sendToMaintenance(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId,
            @RequestBody SendToMaintenanceRequest body) {
        return sendToMaintenanceUseCase.sendToMaintenance(new SendVehicleToMaintenanceCommand(
                currentUser.tenantId(), vehicleId,
                MaintenanceType.valueOf(body.maintenanceType()),
                body.reason(), body.scheduledDate(), body.odometerThresholdKm(), body.technicianName()));
    }

    @Operation(summary = "Mark maintenance as complete")
    @PostMapping("/api/resources/vehicles/{vehicleId}/maintenance/complete")
    @PreAuthorize("isAuthenticated()")
    public Mono<Vehicle> completeMaintenance(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId,
            @RequestParam(required = false) String completionDate) {
        LocalDate date = completionDate != null ? LocalDate.parse(completionDate) : LocalDate.now();
        return completeMaintenanceUseCase.completeMaintenance(currentUser.tenantId(), vehicleId, date);
    }

    @Operation(summary = "Retire a vehicle permanently")
    @PostMapping("/api/resources/vehicles/{vehicleId}/retire")
    @PreAuthorize("isAuthenticated()")
    public Mono<Vehicle> retireVehicle(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId) {
        return retireVehicleUseCase.retireVehicle(currentUser.tenantId(), vehicleId);
    }

    @Operation(summary = "Update vehicle odometer reading")
    @PatchMapping("/api/resources/vehicles/{vehicleId}/odometer")
    @PreAuthorize("isAuthenticated()")
    public Mono<Vehicle> updateOdometer(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId,
            @RequestBody UpdateOdometerRequest body) {
        return updateOdometerUseCase.updateOdometer(
                new UpdateVehicleOdometerCommand(currentUser.tenantId(), vehicleId, body.newOdometerKm()));
    }

    @Operation(summary = "Update vehicle GPS location")
    @PatchMapping("/api/resources/vehicles/{vehicleId}/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public Mono<Void> updateLocation(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId,
            @RequestBody UpdateLocationRequest body) {
        return updateLocationUseCase.updateLocation(
                new UpdateVehicleLocationCommand(currentUser.tenantId(), vehicleId, body.latitude(), body.longitude()));
    }

    @Operation(summary = "Find the best-matching vehicle for a mission")
    @PostMapping("/api/resources/agencies/{agencyId}/vehicles/best-match")
    public Mono<Vehicle> findBestVehicle(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID agencyId,
            @RequestParam double weightKg,
            @RequestParam(defaultValue = "0.1") double volumeM3) {
        return findBestVehicleUseCase.findBestVehicle(
                new FindBestVehicleCommand(currentUser.tenantId(), agencyId, weightKg, volumeM3, null));
    }

    @Operation(summary = "List vehicles with maintenance overdue")
    @GetMapping("/api/resources/agencies/{agencyId}/vehicles/maintenance-due")
    public Flux<Vehicle> checkMaintenanceDue(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID agencyId) {
        return checkMaintenanceDueUseCase.findVehiclesWithMaintenanceDue(currentUser.tenantId(), agencyId);
    }

    @Operation(summary = "Schedule a proactive maintenance alert for a vehicle")
    @PostMapping("/api/resources/vehicles/{vehicleId}/maintenance-alert")
    @PreAuthorize("isAuthenticated()")
    public Mono<Vehicle> scheduleMaintenanceAlert(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID vehicleId,
            @RequestBody ScheduleMaintenanceAlertRequest body) {
        return scheduleMaintenanceAlertUseCase.scheduleMaintenanceAlert(
                new ScheduleMaintenanceAlertCommand(currentUser.tenantId(), vehicleId,
                        MaintenanceType.valueOf(body.maintenanceType()),
                        body.reason(), body.scheduledDate(), body.odometerThresholdKm()));
    }

    // ── Request DTOs ──────────────────────────────────────────────────────────

    public record CreateVehicleRequest(
            UUID organizationId, UUID agencyId,
            String registrationNumber, String brand, String model,
            Integer yearOfManufacture, String type,
            Double maxWeightKg, Double maxVolumeM3, Boolean hasRefrigeration) {}

    public record AssignVehicleRequest(UUID delivererId, UUID missionId) {}

    public record SendToMaintenanceRequest(
            String maintenanceType, String reason,
            LocalDate scheduledDate, Double odometerThresholdKm, String technicianName) {}

    public record UpdateOdometerRequest(Double newOdometerKm) {}

    public record UpdateLocationRequest(Double latitude, Double longitude) {}

    public record ScheduleMaintenanceAlertRequest(
            String maintenanceType, String reason,
            LocalDate scheduledDate, Double odometerThresholdKm) {}
}
