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

import java.util.UUID;

/**
 * REST controller for FreelancerOrg fleet management (vehicles + equipment).
 * Path aligned with Kernel Core's resource management convention at
 * {@code /api/resources/freelancer-orgs/{orgId}/fleet}.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Freelancer Fleet", description = "FreelancerOrg own vehicle and equipment fleet management")
@RestController
@RequestMapping("/api/resources/freelancer-orgs/{orgId}/fleet")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FreelancerFleetController {

    private final AddFreelancerVehicleUseCase addVehicleUseCase;
    private final AddFreelancerEquipmentUseCase addEquipmentUseCase;
    private final AssignFreelancerVehicleToMissionUseCase assignToMissionUseCase;
    private final ReleaseFreelancerVehicleFromMissionUseCase releaseFromMissionUseCase;
    private final ListFreelancerFleetUseCase listFleetUseCase;
    private final GetFreelancerVehicleUseCase getVehicleUseCase;
    private final DeactivateFreelancerVehicleUseCase deactivateVehicleUseCase;

    // ── Vehicle endpoints ─────────────────────────────────────────────────────

    @Operation(summary = "Register a vehicle in the freelancer org fleet")
    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FreelancerVehicle> addVehicle(
            @PathVariable UUID orgId,
            @RequestBody AddVehicleRequest body) {
        AddFreelancerVehicleCommand cmd = new AddFreelancerVehicleCommand(
                orgId,
                VehicleType.valueOf(body.type()),
                body.brand(), body.model(), body.plateNumber(),
                body.maxCapacityKg(), body.volumeM3(),
                body.fuelType() != null ? FuelType.valueOf(body.fuelType()) : null,
                body.fuelConsumptionLPer100km(),
                body.registrationDocRef(), body.insuranceDocRef());
        return addVehicleUseCase.addVehicle(cmd);
    }

    @Operation(summary = "List all vehicles in the freelancer org fleet")
    @GetMapping("/vehicles")
    public Flux<FreelancerVehicle> listVehicles(
            @PathVariable UUID orgId,
            @RequestParam(defaultValue = "false") boolean availableOnly) {
        return availableOnly
                ? listFleetUseCase.getAvailableVehicles(orgId)
                : listFleetUseCase.getAllVehicles(orgId);
    }

    @Operation(summary = "Get a specific vehicle from the fleet")
    @GetMapping("/vehicles/{vehicleId}")
    public Mono<FreelancerVehicle> getVehicle(@PathVariable UUID vehicleId) {
        return getVehicleUseCase.getVehicle(vehicleId);
    }

    @Operation(summary = "Assign a freelancer vehicle to a delivery mission")
    @PostMapping("/vehicles/{vehicleId}/assign-mission")
    public Mono<FreelancerVehicle> assignToMission(
            @PathVariable UUID orgId,
            @PathVariable UUID vehicleId,
            @RequestBody AssignMissionRequest body) {
        return assignToMissionUseCase.assignToMission(
                new AssignFreelancerVehicleToMissionCommand(vehicleId, orgId, body.missionId()));
    }

    @Operation(summary = "Release a freelancer vehicle from its current mission")
    @PostMapping("/vehicles/{vehicleId}/release-mission")
    public Mono<FreelancerVehicle> releaseFromMission(
            @PathVariable UUID vehicleId,
            @RequestParam String missionId) {
        return releaseFromMissionUseCase.releaseFromMission(vehicleId, missionId);
    }

    @Operation(summary = "Deactivate a vehicle (sold, scrapped, long-term storage)")
    @PostMapping("/vehicles/{vehicleId}/deactivate")
    public Mono<FreelancerVehicle> deactivateVehicle(@PathVariable UUID vehicleId) {
        return deactivateVehicleUseCase.deactivateVehicle(vehicleId);
    }

    // ── Equipment endpoints ───────────────────────────────────────────────────

    @Operation(summary = "Add specialized equipment to the freelancer org")
    @PostMapping("/equipments")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FreelancerEquipment> addEquipment(
            @PathVariable UUID orgId,
            @RequestBody AddEquipmentRequest body) {
        AddFreelancerEquipmentCommand cmd = new AddFreelancerEquipmentCommand(
                orgId,
                EquipmentType.valueOf(body.type()),
                body.description(), body.maxCapacityKg(),
                body.ownedOrRented() != null
                        ? OwnershipType.valueOf(body.ownedOrRented()) : OwnershipType.OWNED);
        return addEquipmentUseCase.addEquipment(cmd);
    }

    @Operation(summary = "List all active equipment in the freelancer org")
    @GetMapping("/equipments")
    public Flux<FreelancerEquipment> listEquipments(@PathVariable UUID orgId) {
        return listFleetUseCase.getActiveEquipments(orgId);
    }

    @Operation(summary = "Check whether the org has a specific equipment type")
    @GetMapping("/equipments/has-type")
    public Mono<Boolean> hasEquipmentType(
            @PathVariable UUID orgId,
            @RequestParam String type) {
        return listFleetUseCase.hasEquipmentOfType(orgId, EquipmentType.valueOf(type));
    }

    // ── Request DTOs ──────────────────────────────────────────────────────────

    public record AddVehicleRequest(
            String type, String brand, String model, String plateNumber,
            double maxCapacityKg, Double volumeM3, String fuelType,
            Double fuelConsumptionLPer100km, String registrationDocRef, String insuranceDocRef) {}

    public record AddEquipmentRequest(
            String type, String description, Double maxCapacityKg, String ownedOrRented) {}

    public record AssignMissionRequest(String missionId) {}
}
