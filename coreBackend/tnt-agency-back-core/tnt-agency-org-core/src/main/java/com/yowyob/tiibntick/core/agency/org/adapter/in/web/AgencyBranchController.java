package com.yowyob.tiibntick.core.agency.org.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyBranchResponse;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyBranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "Agency ERP Branches", description = "Agency branch management")
@RestController
@RequiredArgsConstructor
public class AgencyBranchController {

    private final AgencyBranchService branchService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/branches")
    @Operation(summary = "List branches for an agency")
    public Mono<ApiResponse<List<AgencyBranchResponse>>> list(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return branchService.listByAgency(tenantId, agencyId)
                .collectList()
                .map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a branch")
    public Mono<ApiResponse<AgencyBranchResponse>> create(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @Valid @RequestBody CreateBranchRequest req) {
        return branchService.create(new AgencyBranchService.CreateBranchInput(
                tenantId, agencyId, req.name(), req.code(),
                addressField(req.address(), AddressRequest::street),
                addressField(req.address(), AddressRequest::landmark),
                addressField(req.address(), AddressRequest::quarter),
                addressField(req.address(), AddressRequest::city),
                addressField(req.address(), AddressRequest::region),
                addressField(req.address(), AddressRequest::country),
                addressField(req.address(), AddressRequest::postalCode),
                addressField(req.address(), AddressRequest::lat),
                addressField(req.address(), AddressRequest::lon)
        )).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/branches/{branchId}")
    @Operation(summary = "Get branch by ID")
    public Mono<ApiResponse<AgencyBranchResponse>> getById(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId) {
        return branchService.getById(tenantId, branchId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/branches/{branchId}")
    @Operation(summary = "Update a branch")
    public Mono<ApiResponse<AgencyBranchResponse>> update(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchRequest req) {
        return branchService.update(new AgencyBranchService.UpdateBranchInput(
                tenantId, branchId, req.name(),
                addressField(req.address(), AddressRequest::street),
                addressField(req.address(), AddressRequest::landmark),
                addressField(req.address(), AddressRequest::quarter),
                addressField(req.address(), AddressRequest::city),
                addressField(req.address(), AddressRequest::region),
                addressField(req.address(), AddressRequest::country),
                addressField(req.address(), AddressRequest::postalCode),
                addressField(req.address(), AddressRequest::lat),
                addressField(req.address(), AddressRequest::lon)
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/branches/{branchId}/manager")
    @Operation(summary = "Assign branch manager")
    public Mono<ApiResponse<AgencyBranchResponse>> assignManager(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId,
            @RequestParam UUID managerId) {
        return branchService.assignManager(tenantId, branchId, managerId).map(ApiResponse::success);
    }

    @DeleteMapping("/api/v1/tenants/{tenantId}/agency-registry/branches/{branchId}/manager")
    @Operation(summary = "Clear branch manager")
    public Mono<ApiResponse<AgencyBranchResponse>> clearManager(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId) {
        return branchService.clearManager(tenantId, branchId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/branches/{branchId}/status")
    @Operation(summary = "Change branch status")
    public Mono<ApiResponse<AgencyBranchResponse>> changeStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId,
            @RequestParam String status) {
        return branchService.changeStatus(tenantId, branchId, status).map(ApiResponse::success);
    }

    @DeleteMapping("/api/v1/tenants/{tenantId}/agency-registry/branches/{branchId}")
    @Operation(summary = "Close a branch")
    public Mono<ApiResponse<Void>> close(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId) {
        return branchService.close(tenantId, branchId)
                .then(Mono.just(ApiResponse.<Void>success(null)));
    }

    private static <T> T addressField(AddressRequest address, java.util.function.Function<AddressRequest, T> getter) {
        return address != null ? getter.apply(address) : null;
    }

    public record CreateBranchRequest(
            @NotBlank String name,
            @NotBlank String code,
            AddressRequest address) {}

    public record UpdateBranchRequest(
            @NotBlank String name,
            AddressRequest address) {}

    public record AddressRequest(
            String street, String landmark, String quarter,
            @NotBlank String city, String region,
            @NotBlank String country, String postalCode,
            Double lat, Double lon) {}
}
