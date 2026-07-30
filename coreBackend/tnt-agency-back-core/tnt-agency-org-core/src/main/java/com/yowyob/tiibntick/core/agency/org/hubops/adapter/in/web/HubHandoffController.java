package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto.HubHandoffResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubHandoffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "Agency Hub Handoffs", description = "Deposit/withdraw validation queue for agency hubs")
@RestController
@RequiredArgsConstructor
public class HubHandoffController {

    private final HubHandoffService handoffService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/handoffs")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List handoffs for a hub")
    public Mono<ApiResponse<List<HubHandoffResponse>>> list(
            @PathVariable UUID tenantId, @PathVariable UUID hubId) {
        return handoffService.listByHub(tenantId, hubId).collectList().map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/handoffs/pending")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List pending / awaiting-client handoffs")
    public Mono<ApiResponse<List<HubHandoffResponse>>> listPending(
            @PathVariable UUID tenantId, @PathVariable UUID hubId) {
        return handoffService.listPending(tenantId, hubId).collectList().map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/handoffs")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create deposit/withdraw handoff request (scan or manual)")
    public Mono<ApiResponse<HubHandoffResponse>> create(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestBody CreateHandoffRequest body) {
        return handoffService.createRequest(new HubHandoffService.CreateInput(
                tenantId, hubId, body.handoffType(),
                body.missionId(), body.packageId(), body.trackingCode(),
                body.requesterActorId(), body.requesterRole(), body.requesterLabel(),
                body.withdrawParty(), body.notes()
        )).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/hub-handoffs/{handoffId}/approve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Hub operator approves a pending handoff")
    public Mono<ApiResponse<HubHandoffResponse>> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID handoffId,
            @RequestBody ApproveRequest body) {
        return handoffService.approve(new HubHandoffService.ApproveInput(
                tenantId, handoffId, body.validatorActorId(), body.validatorLabel()
        )).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/hub-handoffs/{handoffId}/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Hub operator rejects a handoff")
    public Mono<ApiResponse<HubHandoffResponse>> reject(
            @PathVariable UUID tenantId,
            @PathVariable UUID handoffId,
            @RequestBody RejectRequest body) {
        return handoffService.reject(
                tenantId, handoffId, body.validatorActorId(), body.validatorLabel(), body.notes()
        ).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/handoffs/claim-client-withdraw")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Hub claims client retrieved parcel — awaits client confirm")
    public Mono<ApiResponse<HubHandoffResponse>> claimClientWithdraw(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestBody ClaimClientWithdrawRequest body) {
        return handoffService.claimClientWithdraw(new HubHandoffService.ClaimClientWithdrawInput(
                tenantId, hubId, body.trackingCode(),
                body.missionId(), body.packageId(),
                body.operatorActorId(), body.operatorLabel(), body.notes()
        )).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/hub-handoffs/{handoffId}/confirm-client")
    @Operation(summary = "Client confirms parcel retrieval after hub claim")
    public Mono<ApiResponse<HubHandoffResponse>> confirmClient(
            @PathVariable UUID tenantId,
            @PathVariable UUID handoffId,
            @RequestBody ConfirmClientRequest body) {
        return handoffService.confirmClientWithdraw(
                tenantId, handoffId, body.clientActorId(), body.clientLabel()
        ).map(ApiResponse::success);
    }

    public record CreateHandoffRequest(
            @NotBlank String handoffType,
            @NotBlank String trackingCode,
            UUID missionId,
            UUID packageId,
            UUID requesterActorId,
            String requesterRole,
            String requesterLabel,
            String withdrawParty,
            String notes) {}

    public record ApproveRequest(UUID validatorActorId, String validatorLabel) {}

    public record RejectRequest(UUID validatorActorId, String validatorLabel, String notes) {}

    public record ClaimClientWithdrawRequest(
            @NotBlank String trackingCode,
            UUID missionId,
            UUID packageId,
            UUID operatorActorId,
            String operatorLabel,
            String notes) {}

    public record ConfirmClientRequest(UUID clientActorId, String clientLabel) {}
}
