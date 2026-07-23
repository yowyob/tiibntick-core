package com.yowyob.tiibntick.core.agency.assignment.adapter.in.web;



import com.yowyob.tiibntick.common.api.ApiResponse;

import com.yowyob.tiibntick.core.agency.assignment.adapter.in.web.dto.MissionResponse;

import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PatchMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;



import java.time.Instant;

import java.util.List;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;



@Tag(name = "Agency ERP Missions", description = "Agency mission projection and lifecycle")

@RestController

@RequiredArgsConstructor

public class MissionController {



    private final MissionService missionService;



    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/missions")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Create agency mission projection")

    public Mono<ApiResponse<MissionResponse>> create(

            @PathVariable UUID tenantId,

            @PathVariable UUID agencyId,

            @RequestBody CreateMissionRequest body) {

        return missionService.create(new MissionService.CreateMissionInput(

                tenantId, agencyId, body.branchId(), body.coreMissionId(),

                body.scheduledAt(), body.pickupAddress(), body.deliveryAddress(),

                body.senderName(), body.recipientName(), body.recipientPhone(),

                body.weightKg(), body.distanceKm(), body.packagesCount(),

                body.priority(), body.targetHubId()

        )).map(MissionResponse::from).map(ApiResponse::success);

    }



    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/missions")

    @Operation(summary = "List agency mission projections")

    public Mono<ApiResponse<List<MissionResponse>>> listByAgency(

            @PathVariable UUID tenantId,

            @PathVariable UUID agencyId,

            @RequestParam(required = false) String status) {

        var flux = status != null && !status.isBlank()

                ? missionService.listByAgencyAndStatus(tenantId, agencyId, status)

                : missionService.listByAgency(tenantId, agencyId);

        return flux.map(MissionResponse::from).collectList().map(ApiResponse::success);

    }



    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/missions")

    @Operation(summary = "List missions for deliverer")

    public Mono<ApiResponse<List<MissionResponse>>> listByDeliverer(

            @PathVariable UUID tenantId, @PathVariable UUID delivererId) {

        return missionService.listByDeliverer(tenantId, delivererId)
                .map(MissionResponse::from)
                .collectList()
                .map(ApiResponse::success);

    }



    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}")

    @Operation(summary = "Get agency mission projection")

    public Mono<ApiResponse<MissionResponse>> getById(

            @PathVariable UUID tenantId, @PathVariable UUID missionId) {

        return missionService.getById(tenantId, missionId).map(MissionResponse::from).map(ApiResponse::success);

    }



    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/assign")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Assign deliverer to mission")

    public Mono<ApiResponse<MissionResponse>> assign(

            @PathVariable UUID tenantId,

            @PathVariable UUID missionId,

            @RequestBody AssignMissionRequest body) {

        return missionService.assign(tenantId, missionId, body.delivererId(), body.vehicleId())

                .map(MissionResponse::from)

                .map(ApiResponse::success);

    }



    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/reassign")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Reassign mission")

    public Mono<ApiResponse<MissionResponse>> reassign(

            @PathVariable UUID tenantId,

            @PathVariable UUID missionId,

            @RequestBody AssignMissionRequest body) {

        return missionService.reassign(tenantId, missionId, body.delivererId(), body.vehicleId())

                .map(MissionResponse::from)

                .map(ApiResponse::success);

    }



    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/cancel")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Cancel mission")

    public Mono<ApiResponse<MissionResponse>> cancel(

            @PathVariable UUID tenantId,

            @PathVariable UUID missionId,

            @RequestBody CancelMissionRequest body) {

        return missionService.cancel(tenantId, missionId, body.reason())

                .map(MissionResponse::from)

                .map(ApiResponse::success);

    }



    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/reschedule")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Reschedule mission")

    public Mono<ApiResponse<MissionResponse>> reschedule(

            @PathVariable UUID tenantId,

            @PathVariable UUID missionId,

            @RequestBody RescheduleMissionRequest body) {

        return missionService.reschedule(tenantId, missionId, body.newScheduledAt())

                .map(MissionResponse::from)

                .map(ApiResponse::success);

    }



    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/pickup")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Start mission (pickup)")

    public Mono<ApiResponse<MissionResponse>> pickup(

            @PathVariable UUID tenantId,

            @PathVariable UUID missionId,

            @RequestBody PickupRequest body) {

        return missionService.pickup(tenantId, missionId, body.delivererId())

                .map(MissionResponse::from)

                .map(ApiResponse::success);

    }



    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/deliver")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Confirm delivery")

    public Mono<ApiResponse<MissionResponse>> deliver(

            @PathVariable UUID tenantId,

            @PathVariable UUID missionId,

            @RequestBody DeliverRequest body) {

        return missionService.deliver(tenantId, missionId, body.delivererId(), body.proofReference())

                .map(MissionResponse::from)

                .map(ApiResponse::success);

    }



    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/deposit-hub")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Deposit parcel at relay hub")

    public Mono<ApiResponse<MissionResponse>> depositAtHub(

            @PathVariable UUID tenantId,

            @PathVariable UUID missionId,

            @RequestBody DepositHubRequest body) {

        return missionService.depositAtHub(

                        tenantId, missionId, body.hubId(), body.delivererId(), body.trackingCode())

                .map(MissionResponse::from)

                .map(ApiResponse::success);

    }



    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/anomaly")
    @PreAuthorize("isAuthenticated()")

    @Operation(summary = "Report mission anomaly")

    public Mono<ApiResponse<MissionResponse>> reportAnomaly(

            @PathVariable UUID tenantId,

            @PathVariable UUID missionId,

            @RequestBody AnomalyRequest body) {

        return missionService.reportAnomaly(

                        tenantId, missionId, body.delivererId(),

                        body.anomalyType(), body.description(), body.fatal())

                .map(MissionResponse::from)

                .map(ApiResponse::success);

    }



    public record CreateMissionRequest(

            UUID coreMissionId, Instant scheduledAt,

            String pickupAddress, String deliveryAddress,

            String senderName, String recipientName, String recipientPhone,

            Double weightKg, Double distanceKm, UUID branchId,

            Integer packagesCount, String priority, UUID targetHubId) {}



    public record AssignMissionRequest(UUID delivererId, UUID vehicleId) {}

    public record CancelMissionRequest(String reason) {}

    public record RescheduleMissionRequest(Instant newScheduledAt) {}

    public record PickupRequest(UUID delivererId) {}

    public record DeliverRequest(UUID delivererId, String proofReference) {}

    public record DepositHubRequest(UUID delivererId, UUID hubId, String trackingCode) {}

    public record AnomalyRequest(UUID delivererId, String anomalyType, String description, boolean fatal) {}

}

