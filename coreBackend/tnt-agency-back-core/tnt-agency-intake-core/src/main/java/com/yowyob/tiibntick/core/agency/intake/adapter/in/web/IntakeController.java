package com.yowyob.tiibntick.core.agency.intake.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.intake.adapter.in.web.dto.IntakeContextResponse;
import com.yowyob.tiibntick.core.agency.intake.adapter.in.web.dto.IntakeResponse;
import com.yowyob.tiibntick.core.agency.intake.adapter.in.web.dto.IntakeStatusResponse;
import com.yowyob.tiibntick.core.agency.intake.application.service.IntakeService;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest.DeliveryMode;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest.Source;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Port of tnt-agency {@code IntakeController}. */
@Tag(name = "Agency ERP Intake", description = "Client shipment intake queue")
@RestController
@RequiredArgsConstructor
public class IntakeController {

    private final IntakeService intakeService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/intake-context")
    @Operation(summary = "Agency/branch context for client intake form (QR welcome)")
    public Mono<ApiResponse<IntakeContextResponse>> getContext(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestParam UUID branchId) {
        return intakeService.getContext(tenantId, agencyId, branchId).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/intake-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a client shipment request (mobile)")
    public Mono<ApiResponse<IntakeResponse>> submit(
            @PathVariable UUID tenantId,
            @RequestBody SubmitIntakeRequest body) {
        return intakeService.submit(toSubmitInput(tenantId, body, Source.MOBILE))
                .map(IntakeResponse::from)
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/agency-registry/intake-requests/{reference}")
    @Operation(summary = "Public status lookup by reference code")
    public Mono<ApiResponse<IntakeStatusResponse>> getByReference(@PathVariable String reference) {
        return intakeService.findByReference(reference).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/intake-requests")
    @Operation(summary = "List pending intake requests for agency staff")
    public Mono<ApiResponse<List<IntakeStatusResponse>>> listPending(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return intakeService.listPending(tenantId, agencyId).collectList().map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/intake-requests/{intakeId}/approve")
    @Operation(summary = "Approve intake — creates mission, optional assign + hub deposit")
    public Mono<ApiResponse<IntakeStatusResponse>> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID intakeId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestBody ApproveIntakeRequest body) {
        return intakeService.approve(new IntakeService.ApproveInput(
                intakeId, tenantId, userId,
                body.delivererId(), body.vehicleId(),
                parseMode(body.deliveryMode()), body.targetHubId()))
                .flatMap(intakeService::enrichStatus)
                .map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/intake-requests/{intakeId}/reject")
    @Operation(summary = "Reject a client intake request")
    public Mono<ApiResponse<IntakeStatusResponse>> reject(
            @PathVariable UUID tenantId,
            @PathVariable UUID intakeId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestBody RejectIntakeRequest body) {
        return intakeService.reject(new IntakeService.RejectInput(intakeId, tenantId, userId, body.reason()))
                .flatMap(intakeService::enrichStatus)
                .map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/intake-requests/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Walk-in intake — submit + approve in one call")
    public Mono<ApiResponse<IntakeStatusResponse>> walkIn(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestBody WalkInIntakeRequest body) {
        DeliveryMode mode = parseMode(body.deliveryMode());
        return intakeService.submitAndApproveWalkIn(new IntakeService.WalkInApproveInput(
                tenantId, agencyId, body.branchId(), userId,
                body.senderName(), body.senderPhone(),
                body.recipientName(), body.recipientPhone(),
                body.pickupAddress(), body.deliveryAddress(),
                body.weightKg(), body.packagesCount() != null ? body.packagesCount() : 1,
                mode != null ? mode : DeliveryMode.DIRECT,
                body.targetHubId(), body.notes(),
                body.delivererId(), body.vehicleId(), null, null
        )).flatMap(intakeService::enrichStatus).map(ApiResponse::success);
    }

    private static DeliveryMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        return DeliveryMode.valueOf(mode.trim().toUpperCase());
    }

    private static IntakeService.SubmitInput toSubmitInput(
            UUID tenantId, SubmitIntakeRequest body, Source source) {
        DeliveryMode mode = parseMode(body.deliveryMode());
        return new IntakeService.SubmitInput(
                tenantId, body.agencyId(), body.branchId(), source,
                body.senderName(), body.senderPhone(),
                body.recipientName(), body.recipientPhone(),
                body.pickupAddress(), body.deliveryAddress(),
                body.weightKg(), body.packagesCount() != null ? body.packagesCount() : 1,
                mode != null ? mode : DeliveryMode.DIRECT,
                body.targetHubId(), body.notes());
    }

    record SubmitIntakeRequest(
            UUID agencyId, UUID branchId,
            String senderName, String senderPhone,
            String recipientName, String recipientPhone,
            String pickupAddress, String deliveryAddress,
            Double weightKg, Integer packagesCount,
            String deliveryMode, UUID targetHubId, String notes) {}

    record WalkInIntakeRequest(
            UUID branchId,
            String senderName, String senderPhone,
            String recipientName, String recipientPhone,
            String pickupAddress, String deliveryAddress,
            Double weightKg, Integer packagesCount,
            String deliveryMode, UUID targetHubId, String notes,
            UUID delivererId, UUID vehicleId) {}

    record ApproveIntakeRequest(
            UUID delivererId, UUID vehicleId,
            String deliveryMode, UUID targetHubId) {}

    record RejectIntakeRequest(String reason) {}
}
