package com.yowyob.tiibntick.core.agency.staff.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.staff.adapter.in.web.dto.StaffMemberResponse;
import com.yowyob.tiibntick.core.agency.staff.application.service.StaffMemberService;
import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
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

/**
 * Port of {@code StaffAdminController} — administrative staff (managers, dispatchers).
 */
@Tag(name = "Agency ERP Staff", description = "Agency administrative staff members")
@RestController
@RequiredArgsConstructor
public class StaffMemberController {

    private final StaffMemberService staffService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/staff")
    @Operation(summary = "List administrative staff for an agency")
    public Mono<ApiResponse<List<StaffMemberResponse>>> list(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return staffService.listByAgency(tenantId, agencyId)
                .collectList()
                .map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an administrative staff member")
    public Mono<ApiResponse<StaffMemberResponse>> register(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody RegisterStaffRequest body) {
        return staffService.register(new StaffMemberService.RegisterInput(
                tenantId, agencyId, body.branchId(),
                body.fullName(), body.phone(), body.email(), body.role()
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/staff/{memberId}")
    @Operation(summary = "Update staff member profile")
    public Mono<ApiResponse<StaffMemberResponse>> update(
            @PathVariable UUID tenantId,
            @PathVariable UUID memberId,
            @RequestBody UpdateStaffRequest body) {
        return staffService.update(new StaffMemberService.UpdateInput(
                tenantId, memberId, body.fullName(), body.phone(), body.email(), body.role(), body.branchId()
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/staff/{memberId}/suspend")
    @Operation(summary = "Suspend a staff member")
    public Mono<ApiResponse<StaffMemberResponse>> suspend(
            @PathVariable UUID tenantId,
            @PathVariable UUID memberId) {
        return staffService.suspend(tenantId, memberId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/staff/{memberId}/reactivate")
    @Operation(summary = "Reactivate a suspended staff member")
    public Mono<ApiResponse<StaffMemberResponse>> reactivate(
            @PathVariable UUID tenantId,
            @PathVariable UUID memberId) {
        return staffService.reactivate(tenantId, memberId).map(ApiResponse::success);
    }

    public record RegisterStaffRequest(
            @NotBlank String fullName,
            @NotBlank String phone,
            String email,
            StaffRole role,
            UUID branchId) {}

    public record UpdateStaffRequest(
            String fullName,
            String phone,
            String email,
            StaffRole role,
            UUID branchId) {}
}
