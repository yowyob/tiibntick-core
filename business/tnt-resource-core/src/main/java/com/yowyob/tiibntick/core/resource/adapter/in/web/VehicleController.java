package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.resource.application.port.in.*;
import com.yowyob.tiibntick.core.resource.domain.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public Mono<Vehicle> createVehicle(@RequestBody CreateVehicleRequest body) {
        return createVehicleUseCase.createVehicle(new CreateVehicleCommand(
                body.tenantId(), body.organizationId(), body.agencyId(),
                body.registrationNumber(), body.brand(), body.model(), body.yearOfManufacture(),
                VehicleType.valueOf(body.type()), body.maxWeightKg(), body.maxVolumeM3(),
                body.hasRefrigeration() != null && body.hasRefrigeration()));
    }

    @Operation(summary = "Get vehicle by ID")
    @GetMapping("/api/resources/vehicles/{vehicleId}")
    public Mono<Vehicle> getVehicle(
            @PathVariable UUID vehicleId,
            @RequestParam UUID tenantId) {
        return getVehicleUseCase.getVehicle(tenantId, vehicleId);
    }

    @Operation(summary = "List vehicles for an agency")
    @GetMapping("/api/resources/agencies/{agencyId}/vehicles")
    public Flux<Vehicle> listByAgency(
            @PathVariable UUID agencyId,
            @RequestParam UUID tenantId,
            @RequestParam(required = false) String status) {
        VehicleStatus statusFilter = status != null ? VehicleStatus.valueOf(status) : null;
        return listVehiclesUseCase.listByAgency(tenantId, agencyId, statusFilter);
    }

    @Operation(summary = "Assign vehicle to a deliverer and mission")
    @PostMapping("/api/resources/vehicles/{vehicleId}/assign")
    public Mono<Vehicle> assignVehicle(
            @PathVariable UUID vehicleId,
            @RequestBody AssignVehicleRequest body) {
        return assignVehicleUseCase.assignVehicle(
                new AssignVehicleCommand(body.tenantId(), vehicleId, body.delivererId(), body.missionId()));
    }

    @Operation(summary = "Unassign vehicle from its current deliverer")
    @PostMapping("/api/resources/vehicles/{vehicleId}/unassign")
    public Mono<Vehicle> unassignVehicle(
            @PathVariable UUID vehicleId,
            @RequestParam UUID tenantId) {
        return unassignVehicleUseCase.unassignVehicle(tenantId, vehicleId);
    }

    @Operation(summary = "Send vehicle to maintenance")
    @PostMapping("/api/resources/vehicles/{vehicleId}/maintenance")
    public Mono<Vehicle> sendToMaintenance(
            @PathVariable UUID vehicleId,
            @RequestBody SendToMaintenanceRequest body) {
        return sendToMaintenanceUseCase.sendToMaintenance(new SendVehicleToMaintenanceCommand(
                body.tenantId(), vehicleId,
                MaintenanceType.valueOf(body.maintenanceType()),
                body.reason(), body.scheduledDate(), body.odometerThresholdKm(), body.technicianName()));
    }

    @Operation(summary = "Mark maintenance as complete")
    @PostMapping("/api/resources/vehicles/{vehicleId}/maintenance/complete")
    public Mono<Vehicle> completeMaintenance(
            @PathVariable UUID vehicleId,
            @RequestParam UUID tenantId,
            @RequestParam(required = false) String completionDate) {
        LocalDate date = completionDate != null ? LocalDate.parse(completionDate) : LocalDate.now();
        return completeMaintenanceUseCase.completeMaintenance(tenantId, vehicleId, date);
    }

    @Operation(summary = "Retire a vehicle permanently")
    @PostMapping("/api/resources/vehicles/{vehicleId}/retire")
    public Mono<Vehicle> retireVehicle(
            @PathVariable UUID vehicleId,
            @RequestParam UUID tenantId) {
        return retireVehicleUseCase.retireVehicle(tenantId, vehicleId);
    }

    @Operation(summary = "Update vehicle odometer reading")
    @PatchMapping("/api/resources/vehicles/{vehicleId}/odometer")
    public Mono<Vehicle> updateOdometer(
            @PathVariable UUID vehicleId,
            @RequestBody UpdateOdometerRequest body) {
        return updateOdometerUseCase.updateOdometer(
                new UpdateVehicleOdometerCommand(body.tenantId(), vehicleId, body.newOdometerKm()));
    }

    @Operation(summary = "Update vehicle GPS location")
    @PatchMapping("/api/resources/vehicles/{vehicleId}/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> updateLocation(
            @PathVariable UUID vehicleId,
            @RequestBody UpdateLocationRequest body) {
        return updateLocationUseCase.updateLocation(
                new UpdateVehicleLocationCommand(body.tenantId(), vehicleId, body.latitude(), body.longitude()));
    }

    @Operation(summary = "Find the best-matching vehicle for a mission")
    @PostMapping("/api/resources/agencies/{agencyId}/vehicles/best-match")
    public Mono<Vehicle> findBestVehicle(
            @PathVariable UUID agencyId,
            @RequestParam UUID tenantId,
            @RequestParam double weightKg,
            @RequestParam(defaultValue = "0.1") double volumeM3) {
        return findBestVehicleUseCase.findBestVehicle(
                new FindBestVehicleCommand(tenantId, agencyId, weightKg, volumeM3, null));
    }

    @Operation(summary = "List vehicles with maintenance overdue")
    @GetMapping("/api/resources/agencies/{agencyId}/vehicles/maintenance-due")
    public Flux<Vehicle> checkMaintenanceDue(
            @PathVariable UUID agencyId,
            @RequestParam UUID tenantId) {
        return checkMaintenanceDueUseCase.findVehiclesWithMaintenanceDue(tenantId, agencyId);
    }

    @Operation(summary = "Schedule a proactive maintenance alert for a vehicle")
    @PostMapping("/api/resources/vehicles/{vehicleId}/maintenance-alert")
    public Mono<Vehicle> scheduleMaintenanceAlert(
            @PathVariable UUID vehicleId,
            @RequestBody ScheduleMaintenanceAlertRequest body) {
        return scheduleMaintenanceAlertUseCase.scheduleMaintenanceAlert(
                new ScheduleMaintenanceAlertCommand(body.tenantId(), vehicleId,
                        MaintenanceType.valueOf(body.maintenanceType()),
                        body.reason(), body.scheduledDate(), body.odometerThresholdKm()));
    }

    // ── Request DTOs ──────────────────────────────────────────────────────────

    public record CreateVehicleRequest(
            UUID tenantId, UUID organizationId, UUID agencyId,
            String registrationNumber, String brand, String model,
            Integer yearOfManufacture, String type,
            Double maxWeightKg, Double maxVolumeM3, Boolean hasRefrigeration) {}

    public record AssignVehicleRequest(UUID tenantId, UUID delivererId, UUID missionId) {}

    public record SendToMaintenanceRequest(
            UUID tenantId, String maintenanceType, String reason,
            LocalDate scheduledDate, Double odometerThresholdKm, String technicianName) {}

    public record UpdateOdometerRequest(UUID tenantId, Double newOdometerKm) {}

    public record UpdateLocationRequest(UUID tenantId, Double latitude, Double longitude) {}

    public record ScheduleMaintenanceAlertRequest(
            UUID tenantId, String maintenanceType, String reason,
            LocalDate scheduledDate, Double odometerThresholdKm) {}
}
