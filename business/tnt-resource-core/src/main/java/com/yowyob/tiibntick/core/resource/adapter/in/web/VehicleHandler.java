package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.resource.application.port.in.*;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleStatus;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;
import com.yowyob.tiibntick.core.auth.adapter.in.web.ReactiveSecurityContextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Web handler for Vehicle-related HTTP endpoints.
 * Implements the driving side of the hexagonal architecture for tnt-resource-core.
 * All endpoints are reactive (non-blocking).
 *
 * @author MANFOUO Braun.
 */
@Component
public class VehicleHandler {

    private final CreateVehicleUseCase createVehicleUseCase;
    private final AssignVehicleUseCase assignVehicleUseCase;
    private final UnassignVehicleUseCase unassignVehicleUseCase;
    private final SendVehicleToMaintenanceUseCase sendToMaintenanceUseCase;
    private final CompleteVehicleMaintenanceUseCase completeMaintenanceUseCase;
    private final RetireVehicleUseCase retireVehicleUseCase;
    private final UpdateVehicleOdometerUseCase updateOdometerUseCase;
    private final UpdateVehicleLocationUseCase updateLocationUseCase;
    private final GetVehicleUseCase getVehicleUseCase;
    private final ListVehiclesByAgencyUseCase listVehiclesUseCase;
    private final FindBestVehicleForMissionUseCase findBestVehicleUseCase;
    private final CheckMaintenanceDueUseCase checkMaintenanceDueUseCase;
    private final ScheduleMaintenanceAlertUseCase scheduleMaintenanceAlertUseCase;

    /**
     * Optional security context extractor from tnt-auth-core.
     * Used to extract tenant/actor IDs from JWT in handler methods that receive
     * a ServerRequest (functional routing style). When absent, tenantId is read
     * from query parameters (backward compatible).
     */
    @Autowired(required = false)
    private ReactiveSecurityContextExtractor securityExtractor;

    public VehicleHandler(
            CreateVehicleUseCase createVehicleUseCase,
            AssignVehicleUseCase assignVehicleUseCase,
            UnassignVehicleUseCase unassignVehicleUseCase,
            SendVehicleToMaintenanceUseCase sendToMaintenanceUseCase,
            CompleteVehicleMaintenanceUseCase completeMaintenanceUseCase,
            RetireVehicleUseCase retireVehicleUseCase,
            UpdateVehicleOdometerUseCase updateOdometerUseCase,
            UpdateVehicleLocationUseCase updateLocationUseCase,
            GetVehicleUseCase getVehicleUseCase,
            ListVehiclesByAgencyUseCase listVehiclesUseCase,
            FindBestVehicleForMissionUseCase findBestVehicleUseCase,
            CheckMaintenanceDueUseCase checkMaintenanceDueUseCase,
            ScheduleMaintenanceAlertUseCase scheduleMaintenanceAlertUseCase) {
        this.createVehicleUseCase = createVehicleUseCase;
        this.assignVehicleUseCase = assignVehicleUseCase;
        this.unassignVehicleUseCase = unassignVehicleUseCase;
        this.sendToMaintenanceUseCase = sendToMaintenanceUseCase;
        this.completeMaintenanceUseCase = completeMaintenanceUseCase;
        this.retireVehicleUseCase = retireVehicleUseCase;
        this.updateOdometerUseCase = updateOdometerUseCase;
        this.updateLocationUseCase = updateLocationUseCase;
        this.getVehicleUseCase = getVehicleUseCase;
        this.listVehiclesUseCase = listVehiclesUseCase;
        this.findBestVehicleUseCase = findBestVehicleUseCase;
        this.checkMaintenanceDueUseCase = checkMaintenanceDueUseCase;
        this.scheduleMaintenanceAlertUseCase = scheduleMaintenanceAlertUseCase;
    }

    /** GET /api/v1/resources/vehicles/{vehicleId}?tenantId=... */
    public Mono<ServerResponse> getVehicle(ServerRequest request) {
        // Prefer JWT tenantId (tnt-auth-core) over query param for security
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        Mono<UUID> tenantIdMono = resolveTenantId(request);
        return tenantIdMono
                .flatMap(tenantId -> getVehicleUseCase.getVehicle(tenantId, vehicleId))
                .flatMap(v -> ServerResponse.ok().bodyValue(v))
                .onErrorResume(e -> ServerResponse.notFound().build());
    }

    /** GET /api/v1/resources/agencies/{agencyId}/vehicles?status=... */
    public Mono<ServerResponse> listVehiclesByAgency(ServerRequest request) {
        UUID agencyId = UUID.fromString(request.pathVariable("agencyId"));
        VehicleStatus status = request.queryParam("status")
                .map(VehicleStatus::valueOf).orElse(null);
        return resolveTenantId(request)
                .flatMapMany(tenantId -> listVehiclesUseCase.listByAgency(tenantId, agencyId, status))
                .collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    /** POST /api/v1/resources/vehicles */
    public Mono<ServerResponse> createVehicle(ServerRequest request) {
        return request.bodyToMono(CreateVehicleRequest.class)
                .map(r -> new CreateVehicleCommand(r.tenantId(), r.organizationId(), r.agencyId(),
                        r.registrationNumber(), r.brand(), r.model(), r.yearOfManufacture(),
                        VehicleType.valueOf(r.type()), r.maxWeightKg(), r.maxVolumeM3(),
                        r.hasRefrigeration() != null && r.hasRefrigeration()))
                .flatMap(createVehicleUseCase::createVehicle)
                .flatMap(v -> ServerResponse.status(201).bodyValue(v));
    }

    /** POST /api/v1/resources/vehicles/{vehicleId}/assign */
    public Mono<ServerResponse> assignVehicle(ServerRequest request) {
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return request.bodyToMono(AssignVehicleRequest.class)
                .map(r -> new AssignVehicleCommand(r.tenantId(), vehicleId, r.delivererId(), r.missionId()))
                .flatMap(assignVehicleUseCase::assignVehicle)
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    /** POST /api/v1/resources/vehicles/{vehicleId}/unassign */
    public Mono<ServerResponse> unassignVehicle(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.queryParam("tenantId").orElseThrow());
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return unassignVehicleUseCase.unassignVehicle(tenantId, vehicleId)
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    /** POST /api/v1/resources/vehicles/{vehicleId}/maintenance */
    public Mono<ServerResponse> sendToMaintenance(ServerRequest request) {
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return request.bodyToMono(SendToMaintenanceRequest.class)
                .map(r -> new SendVehicleToMaintenanceCommand(r.tenantId(), vehicleId,
                        com.yowyob.tiibntick.core.resource.domain.model.MaintenanceType.valueOf(r.maintenanceType()),
                        r.reason(), r.scheduledDate(), r.odometerThresholdKm(), r.technicianName()))
                .flatMap(sendToMaintenanceUseCase::sendToMaintenance)
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    /** POST /api/v1/resources/vehicles/{vehicleId}/maintenance/complete */
    public Mono<ServerResponse> completeMaintenance(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.queryParam("tenantId").orElseThrow());
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        LocalDate completionDate = request.queryParam("completionDate")
                .map(LocalDate::parse).orElse(LocalDate.now());
        return completeMaintenanceUseCase.completeMaintenance(tenantId, vehicleId, completionDate)
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    /** POST /api/v1/resources/vehicles/{vehicleId}/retire */
    public Mono<ServerResponse> retireVehicle(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.queryParam("tenantId").orElseThrow());
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return retireVehicleUseCase.retireVehicle(tenantId, vehicleId)
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    /** PATCH /api/v1/resources/vehicles/{vehicleId}/odometer */
    public Mono<ServerResponse> updateOdometer(ServerRequest request) {
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return request.bodyToMono(UpdateOdometerRequest.class)
                .map(r -> new UpdateVehicleOdometerCommand(r.tenantId(), vehicleId, r.newOdometerKm()))
                .flatMap(updateOdometerUseCase::updateOdometer)
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    /** PATCH /api/v1/resources/vehicles/{vehicleId}/location */
    public Mono<ServerResponse> updateLocation(ServerRequest request) {
        UUID vehicleId = UUID.fromString(request.pathVariable("vehicleId"));
        return request.bodyToMono(UpdateLocationRequest.class)
                .map(r -> new UpdateVehicleLocationCommand(r.tenantId(), vehicleId, r.latitude(), r.longitude()))
                .flatMap(updateLocationUseCase::updateLocation)
                .flatMap(v -> ServerResponse.noContent().build());
    }

    /** GET /api/v1/resources/agencies/{agencyId}/vehicles/best-match?... */
    public Mono<ServerResponse> findBestVehicle(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.queryParam("tenantId").orElseThrow());
        UUID agencyId = UUID.fromString(request.pathVariable("agencyId"));
        double weightKg = Double.parseDouble(request.queryParam("weightKg").orElseThrow());
        double volumeM3 = Double.parseDouble(request.queryParam("volumeM3").orElse("0.1"));
        FindBestVehicleCommand cmd = new FindBestVehicleCommand(tenantId, agencyId, weightKg, volumeM3, null);
        return findBestVehicleUseCase.findBestVehicle(cmd)
                .flatMap(v -> ServerResponse.ok().bodyValue(v))
                .onErrorResume(e -> ServerResponse.notFound().build());
    }

    /** GET /api/v1/resources/agencies/{agencyId}/vehicles/maintenance-due */
    public Mono<ServerResponse> checkMaintenanceDue(ServerRequest request) {
        UUID agencyId = UUID.fromString(request.pathVariable("agencyId"));
        return resolveTenantId(request)
                .flatMapMany(tenantId -> checkMaintenanceDueUseCase.findVehiclesWithMaintenanceDue(tenantId, agencyId))
                .collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    /** POST /api/v1/resources/vehicles/{id}/maintenance-alert */
    public Mono<ServerResponse> scheduleMaintenanceAlert(ServerRequest request) {
        UUID vehicleId = UUID.fromString(request.pathVariable("id"));
        return request.bodyToMono(ScheduleMaintenanceAlertRequest.class)
                .map(r -> new ScheduleMaintenanceAlertCommand(r.tenantId(), vehicleId,
                        com.yowyob.tiibntick.core.resource.domain.model.MaintenanceType.valueOf(r.maintenanceType()),
                        r.reason(), r.scheduledDate(), r.odometerThresholdKm()))
                .flatMap(scheduleMaintenanceAlertUseCase::scheduleMaintenanceAlert)
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    // ---- Request DTOs ----
    public record CreateVehicleRequest(UUID tenantId, UUID organizationId, UUID agencyId,
            String registrationNumber, String brand, String model, int yearOfManufacture,
            String type, double maxWeightKg, double maxVolumeM3, Boolean hasRefrigeration) {}

    public record AssignVehicleRequest(UUID tenantId, UUID delivererId, UUID missionId) {}

    public record SendToMaintenanceRequest(UUID tenantId, String maintenanceType, String reason,
            LocalDate scheduledDate, Double odometerThresholdKm, String technicianName) {}

    public record UpdateOdometerRequest(UUID tenantId, double newOdometerKm) {}

    public record UpdateLocationRequest(UUID tenantId, double latitude, double longitude) {}

    public record ScheduleMaintenanceAlertRequest(UUID tenantId, String maintenanceType, String reason,
            LocalDate scheduledDate, Double odometerThresholdKm) {}

    // ── Security helpers (tnt-auth-core) ─────────────────────────────────

    /**
     * Resolves the tenantId preferring the JWT claim (via tnt-auth-core
     * {@link ReactiveSecurityContextExtractor}) over the query parameter.
     * Falls back to query parameter for backward compatibility and test environments.
     */
    private Mono<UUID> resolveTenantId(ServerRequest request) {
        if (securityExtractor != null) {
            return securityExtractor.requireTenantId()
                    .onErrorResume(e -> {
                        // Fallback to query param if JWT context is unavailable
                        return request.queryParam("tenantId")
                                .map(id -> Mono.just(UUID.fromString(id)))
                                .orElse(Mono.error(e));
                    });
        }
        // No auth-core — always use query param
        return Mono.just(UUID.fromString(request.queryParam("tenantId").orElseThrow()));
    }
}

