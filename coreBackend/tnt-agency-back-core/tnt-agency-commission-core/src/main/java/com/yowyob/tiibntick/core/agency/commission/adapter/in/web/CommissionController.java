package com.yowyob.tiibntick.core.agency.commission.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.commission.adapter.in.web.dto.CommissionResponse;
import com.yowyob.tiibntick.core.agency.commission.application.service.CommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/** Port of commission endpoints from tnt-agency {@code StaffController}. */
@Tag(name = "Agency ERP Commissions", description = "Commission records lifecycle")
@RestController
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService commissionService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/commissions")
    @Operation(summary = "List commission records for an agency")
    public Mono<ApiResponse<List<CommissionResponse>>> listByAgency(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return commissionService.listByAgency(tenantId, agencyId).collectList().map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/commissions")
    @Operation(summary = "List commission records for a deliverer")
    public Mono<ApiResponse<List<CommissionResponse>>> listByDeliverer(
            @PathVariable UUID tenantId, @PathVariable UUID delivererId) {
        return commissionService.listByDeliverer(tenantId, delivererId).collectList().map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/commissions/{commissionId}")
    public Mono<ApiResponse<CommissionResponse>> getById(
            @PathVariable UUID tenantId, @PathVariable UUID commissionId) {
        return commissionService.getById(tenantId, commissionId).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/commissions")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a commission record")
    public Mono<ApiResponse<CommissionResponse>> create(
            @PathVariable UUID tenantId,
            @RequestBody CreateCommissionRequest body) {
        return commissionService.create(new CommissionService.CreateInput(
                tenantId, body.agencyId(), body.delivererId(), body.missionId(),
                body.amount(), body.currency()
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/commissions/{commissionId}/validate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate a commission (CALCULATED → VALIDATED)")
    public Mono<ApiResponse<CommissionResponse>> validate(
            @PathVariable UUID tenantId, @PathVariable UUID commissionId) {
        return commissionService.validate(tenantId, commissionId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/commissions/{commissionId}/pay")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark commission as paid and trigger wallet payout")
    public Mono<ApiResponse<CommissionResponse>> pay(
            @PathVariable UUID tenantId, @PathVariable UUID commissionId) {
        return commissionService.pay(tenantId, commissionId).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/commissions/{commissionId}/dispute")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Dispute a commission")
    public Mono<ApiResponse<CommissionResponse>> dispute(
            @PathVariable UUID tenantId,
            @PathVariable UUID commissionId,
            @RequestBody DisputeRequest body) {
        return commissionService.dispute(tenantId, commissionId, body.reason()).map(ApiResponse::success);
    }

    public record CreateCommissionRequest(
            UUID agencyId, UUID delivererId, UUID missionId, BigDecimal amount, String currency) {}

    public record DisputeRequest(String reason) {}
}
