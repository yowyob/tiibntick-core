package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.resource.application.port.in.*;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import com.yowyob.tiibntick.core.resource.domain.model.FuelType;
import com.yowyob.tiibntick.core.resource.domain.model.OwnershipType;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Web handler for FreelancerOrganization fleet HTTP endpoints.
 *
 * <p>Base path: {@code /api/v1/resources/freelancer-orgs/{orgId}/fleet}
 * All endpoints are reactive (non-blocking WebFlux).
 *
 * @author MANFOUO Braun
 */
@Component
public class FreelancerVehicleHandler {

    private final AddFreelancerVehicleUseCase addVehicleUseCase;
    private final AddFreelancerEquipmentUseCase addEquipmentUseCase;
    private final AssignFreelancerVehicleToMissionUseCase assignToMissionUseCase;
    private final ReleaseFreelancerVehicleFromMissionUseCase releaseFromMissionUseCase;
    private final ListFreelancerFleetUseCase listFleetUseCase;
    private final GetFreelancerVehicleUseCase getVehicleUseCase;
    private final DeactivateFreelancerVehicleUseCase deactivateVehicleUseCase;

    public FreelancerVehicleHandler(
            AddFreelancerVehicleUseCase addVehicleUseCase,
            AddFreelancerEquipmentUseCase addEquipmentUseCase,
            AssignFreelancerVehicleToMissionUseCase assignToMissionUseCase,
            ReleaseFreelancerVehicleFromMissionUseCase releaseFromMissionUseCase,
            ListFreelancerFleetUseCase listFleetUseCase,
            GetFreelancerVehicleUseCase getVehicleUseCase,
            DeactivateFreelancerVehicleUseCase deactivateVehicleUseCase) {
        this.addVehicleUseCase = addVehicleUseCase;
        this.addEquipmentUseCase = addEquipmentUseCase;
        this.assignToMissionUseCase = assignToMissionUseCase;
        this.releaseFromMissionUseCase = releaseFromMissionUseCase;
        this.listFleetUseCase = listFleetUseCase;
        this.getVehicleUseCase = getVehicleUseCase;
        this.deactivateVehicleUseCase = deactivateVehicleUseCase;
    }

    /**
     * POST /api/v1/resources/freelancer-orgs/{orgId}/fleet/vehicles
     * Registers a new vehicle in the FreelancerOrg fleet.
     */
    public Mono<ServerResponse> addVehicle(ServerRequest request) {
        UUID orgId = UUID.fromString(request.pathVariable("orgId"));
        return request.bodyToMono(AddVehicleRequest.class)
                .flatMap(body -> {
                    AddFreelancerVehicleCommand cmd = new AddFreelancerVehicleCommand(
                            orgId,
                            VehicleType.valueOf(body.type()),
                            body.brand(), body.model(), body.plateNumber(),
                            body.maxCapacityKg(), body.volumeM3(),
                            body.fuelType() != null ? FuelType.valueOf(body.fuelType()) : null,
                            body.fuelConsumptionLPer100km(),
                            body.registrationDocRef(), body.insuranceDocRef());
                    return addVehicleUseCase.addVehicle(cmd);
                })
                .flatMap(v -> ServerResponse.status(201).bodyValue(v))
                .onErrorResume(IllegalArgumentException.class, e ->
                        ServerResponse.badRequest().bodyValue(e.getMessage()))
                .onErrorResume(com.yowyob.tiibntick.core.resource.domain.exception.FreelancerFleetCapacityExceededException.class, e ->
                        ServerResponse.status(409).bodyValue(e.getMessage()));
    }

    /**
     * GET /api/v1/resources/freelancer-orgs/{orgId}/fleet/vehicles
     * Lists all vehicles in the FreelancerOrg fleet.
     */
    public Mono<ServerResponse> listVehicles(ServerRequest request) {
        UUID orgId = UUID.fromString(request.pathVariable("orgId"));
        boolean availableOnly = request.queryParam("available").map(Boolean::valueOf).orElse(false);
        var vehicles = availableOnly
                ? listFleetUseCase.getAvailableVehicles(orgId)
                : listFleetUseCase.getAllVehicles(orgId);
        return ServerResponse.ok().body(vehicles,
                com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle.class);
    }

    /**
     * GET /api/v1/resources/freelancer-orgs/{orgId}/fleet/vehicles/{vehicleId}
     * Gets a single FreelancerVehicle by ID.
     */
    public Mono<ServerResponse> getVehicle(ServerRequest request) {
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return getVehicleUseCase.getVehicle(vehicleId)
                .flatMap(v -> ServerResponse.ok().bodyValue(v))
                .onErrorResume(com.yowyob.tiibntick.core.resource.domain.exception.FreelancerVehicleNotFoundException.class, e ->
                        ServerResponse.notFound().build());
    }

    /**
     * POST /api/v1/resources/freelancer-orgs/{orgId}/fleet/vehicles/{vehicleId}/assign-mission
     * Assigns a vehicle to a delivery mission.
     */
    public Mono<ServerResponse> assignToMission(ServerRequest request) {
        UUID orgId = UUID.fromString(request.pathVariable("orgId"));
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return request.bodyToMono(AssignMissionRequest.class)
                .flatMap(body -> assignToMissionUseCase.assignToMission(
                        new AssignFreelancerVehicleToMissionCommand(vehicleId, orgId, body.missionId())))
                .flatMap(v -> ServerResponse.ok().bodyValue(v))
                .onErrorResume(IllegalStateException.class, e ->
                        ServerResponse.status(409).bodyValue(e.getMessage()));
    }

    /**
     * POST /api/v1/resources/freelancer-orgs/{orgId}/fleet/vehicles/{vehicleId}/release-mission
     * Releases a vehicle from its current mission.
     */
    public Mono<ServerResponse> releaseFromMission(ServerRequest request) {
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        String missionId = request.queryParam("missionId").orElseThrow();
        return releaseFromMissionUseCase.releaseFromMission(vehicleId, missionId)
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    /**
     * POST /api/v1/resources/freelancer-orgs/{orgId}/fleet/vehicles/{vehicleId}/deactivate
     * Deactivates a vehicle (sold, scrapped, long-term storage).
     */
    public Mono<ServerResponse> deactivateVehicle(ServerRequest request) {
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return deactivateVehicleUseCase.deactivateVehicle(vehicleId)
                .flatMap(v -> ServerResponse.ok().bodyValue(v))
                .onErrorResume(IllegalStateException.class, e ->
                        ServerResponse.status(409).bodyValue(e.getMessage()));
    }

    /**
     * POST /api/v1/resources/freelancer-orgs/{orgId}/fleet/equipments
     * Adds specialized equipment to the FreelancerOrg.
     */
    public Mono<ServerResponse> addEquipment(ServerRequest request) {
        UUID orgId = UUID.fromString(request.pathVariable("orgId"));
        return request.bodyToMono(AddEquipmentRequest.class)
                .flatMap(body -> {
                    AddFreelancerEquipmentCommand cmd = new AddFreelancerEquipmentCommand(
                            orgId,
                            EquipmentType.valueOf(body.type()),
                            body.description(), body.maxCapacityKg(),
                            body.ownedOrRented() != null ? OwnershipType.valueOf(body.ownedOrRented()) : OwnershipType.OWNED);
                    return addEquipmentUseCase.addEquipment(cmd);
                })
                .flatMap(eq -> ServerResponse.status(201).bodyValue(eq));
    }

    /**
     * GET /api/v1/resources/freelancer-orgs/{orgId}/fleet/equipments
     * Lists all active equipment in the FreelancerOrg.
     */
    public Mono<ServerResponse> listEquipments(ServerRequest request) {
        UUID orgId = UUID.fromString(request.pathVariable("orgId"));
        return ServerResponse.ok().body(listFleetUseCase.getActiveEquipments(orgId),
                com.yowyob.tiibntick.core.resource.domain.model.FreelancerEquipment.class);
    }

    /**
     * GET /api/v1/resources/freelancer-orgs/{orgId}/fleet/equipments/has-type?type=REFRIGERATED_BOX
     * Checks if the org has a specific equipment type.
     */
    public Mono<ServerResponse> hasEquipmentType(ServerRequest request) {
        UUID orgId = UUID.fromString(request.pathVariable("orgId"));
        EquipmentType type = EquipmentType.valueOf(request.queryParam("type").orElseThrow());
        return listFleetUseCase.hasEquipmentOfType(orgId, type)
                .flatMap(has -> ServerResponse.ok().bodyValue(has));
    }

    // ── Request body records ──────────────────────────────────────────────

    public record AddVehicleRequest(String type, String brand, String model,
            String plateNumber, double maxCapacityKg, Double volumeM3, String fuelType,
            Double fuelConsumptionLPer100km, String registrationDocRef, String insuranceDocRef) {}

    public record AddEquipmentRequest(String type, String description,
            Double maxCapacityKg, String ownedOrRented) {}

    public record AssignMissionRequest(String missionId) {}
}
