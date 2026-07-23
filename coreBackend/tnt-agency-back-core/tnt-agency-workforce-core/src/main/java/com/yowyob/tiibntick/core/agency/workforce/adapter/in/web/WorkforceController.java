package com.yowyob.tiibntick.core.agency.workforce.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.ContractResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.DelivererResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.FreelancerAssociationResponse;
import com.yowyob.tiibntick.core.agency.workforce.application.service.ContractService;
import com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyDelivererService;
import com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyFreelancerAssociationService;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.ContractType;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.DelivererStatus;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.RemunerationModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Port of tnt-agency {@code StaffController} (deliverers, contracts, freelancers — no commissions).
 */
@Tag(name = "Agency ERP Workforce", description = "Deliverers, contracts and freelancer associations")
@RestController
@RequiredArgsConstructor
public class WorkforceController {

    private final AgencyDelivererService delivererService;
    private final ContractService contractService;
    private final AgencyFreelancerAssociationService freelancerService;

    // ── Deliverers ─────────────────────────────────────────────────────────

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/deliverers")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a deliverer (links existing Core actor)")
    public Mono<ApiResponse<DelivererResponse>> registerDeliverer(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody RegisterDelivererRequest body) {
        return delivererService.register(new AgencyDelivererService.RegisterInput(
                tenantId, agencyId, body.actorId(), body.phone()
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/branch")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Attach deliverer to a branch")
    public Mono<ApiResponse<DelivererResponse>> attachToBranch(
            @PathVariable UUID tenantId,
            @PathVariable UUID delivererId,
            @RequestBody AttachBranchRequest body) {
        return delivererService.attachToBranch(tenantId, delivererId, body.branchId()).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/suspend")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<DelivererResponse>> suspendDeliverer(
            @PathVariable UUID tenantId, @PathVariable UUID delivererId) {
        return delivererService.suspend(tenantId, delivererId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/reactivate")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<DelivererResponse>> reactivateDeliverer(
            @PathVariable UUID tenantId, @PathVariable UUID delivererId) {
        return delivererService.reactivate(tenantId, delivererId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/availability")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<DelivererResponse>> updateAvailability(
            @PathVariable UUID tenantId,
            @PathVariable UUID delivererId,
            @RequestBody AvailabilityRequest body) {
        return delivererService.updateAvailability(
                tenantId, delivererId, DelivererStatus.valueOf(body.status())
        ).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}")
    public Mono<ApiResponse<DelivererResponse>> getDeliverer(
            @PathVariable UUID tenantId, @PathVariable UUID delivererId) {
        return delivererService.getById(tenantId, delivererId).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/deliverers")
    public Mono<ApiResponse<List<DelivererResponse>>> listDeliverers(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return delivererService.listByAgency(tenantId, agencyId).collectList().map(ApiResponse::success);
    }

    // ── Contracts ────────────────────────────────────────────────────────

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/contracts")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<ContractResponse>> signContract(
            @PathVariable UUID tenantId,
            @PathVariable UUID delivererId,
            @RequestBody SignContractRequest body) {
        return contractService.sign(new ContractService.SignInput(
                tenantId, body.agencyId(), delivererId,
                body.contractType(), body.startDate(), body.endDate(),
                body.remunerationModel(), body.baseSalary(), body.commissionRate()
        )).map(ApiResponse::success);
    }

    @DeleteMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/contracts/active")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<ContractResponse>> terminateContract(
            @PathVariable UUID tenantId, @PathVariable UUID delivererId) {
        return contractService.terminate(tenantId, delivererId).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/contracts")
    public Mono<ApiResponse<List<ContractResponse>>> listContractsByAgency(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return contractService.listByAgency(tenantId, agencyId).collectList().map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/contracts")
    public Mono<ApiResponse<List<ContractResponse>>> listContractsByDeliverer(
            @PathVariable UUID tenantId, @PathVariable UUID delivererId) {
        return contractService.listByDeliverer(tenantId, delivererId).collectList().map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/deliverers/{delivererId}/contracts/active")
    public Mono<ApiResponse<ContractResponse>> getActiveContract(
            @PathVariable UUID tenantId, @PathVariable UUID delivererId) {
        return contractService.getActiveByDeliverer(tenantId, delivererId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/contracts/{contractId}/remuneration")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<ContractResponse>> updateRemuneration(
            @PathVariable UUID tenantId,
            @PathVariable UUID contractId,
            @RequestBody UpdateRemunerationRequest body) {
        return contractService.updateRemuneration(
                tenantId, contractId, body.baseSalary(), body.commissionRate()
        ).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/contracts/{contractId}/renew")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<ContractResponse>> renewContract(
            @PathVariable UUID tenantId,
            @PathVariable UUID contractId,
            @RequestBody RenewContractRequest body) {
        return contractService.renew(tenantId, contractId, body.endDate()).map(ApiResponse::success);
    }

    // ── Freelancers ────────────────────────────────────────────────────────

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/freelancers")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<FreelancerAssociationResponse>> associateFreelancer(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody AssociateFreelancerRequest body) {
        return freelancerService.associate(new AgencyFreelancerAssociationService.AssociateInput(
                tenantId, agencyId, body.freelancerActorId(), body.commissionRate(), body.startDate()
        )).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/freelancers/associations/{associationId}/end")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<FreelancerAssociationResponse>> endAssociation(
            @PathVariable UUID tenantId,
            @PathVariable UUID associationId,
            @RequestBody EndAssociationRequest body) {
        return freelancerService.end(tenantId, associationId, body.endDate()).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/freelancers/associations/{associationId}/pause")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<FreelancerAssociationResponse>> pauseAssociation(
            @PathVariable UUID tenantId, @PathVariable UUID associationId) {
        return freelancerService.pause(tenantId, associationId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/freelancers/associations/{associationId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<FreelancerAssociationResponse>> cancelInvitation(
            @PathVariable UUID tenantId, @PathVariable UUID associationId) {
        return freelancerService.cancelInvitation(tenantId, associationId).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/freelancers")
    public Mono<ApiResponse<List<FreelancerAssociationResponse>>> listFreelancers(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return freelancerService.listByAgency(tenantId, agencyId).collectList().map(ApiResponse::success);
    }

    public record RegisterDelivererRequest(UUID actorId, String fullName, String email, String phone) {}
    public record AttachBranchRequest(UUID branchId) {}
    public record AvailabilityRequest(String status) {}
    public record SignContractRequest(
            UUID agencyId, ContractType contractType, LocalDate startDate, LocalDate endDate,
            RemunerationModel remunerationModel, BigDecimal baseSalary, BigDecimal commissionRate) {}
    public record UpdateRemunerationRequest(BigDecimal baseSalary, BigDecimal commissionRate) {}
    public record RenewContractRequest(LocalDate endDate) {}
    public record AssociateFreelancerRequest(UUID freelancerActorId, BigDecimal commissionRate, LocalDate startDate) {}
    public record EndAssociationRequest(LocalDate endDate) {}
}
