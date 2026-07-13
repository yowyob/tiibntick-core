package com.yowyob.tiibntick.core.agency.billing.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.billing.application.service.BillingService;
import com.yowyob.tiibntick.core.agency.billing.domain.BillingPolicy;
import com.yowyob.tiibntick.core.agency.billing.domain.InvoiceRecord;
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

@Tag(name = "Agency ERP Billing", description = "Billing policy, estimate and invoice lifecycle")
@RestController
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/billing-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create billing policy")
    public Mono<ApiResponse<PolicyResponse>> createPolicy(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestBody CreatePolicyRequest body) {
        return billingService.createPolicy(new BillingService.CreatePolicyInput(
                        tenantId, agencyId, body.name(), body.description(), body.currency(),
                        body.basePrice(), body.pricePerKm(), body.pricePerKg(), body.minPrice()
                ))
                .map(PolicyResponse::from)
                .map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/billing-policies/{policyId}/activate")
    @Operation(summary = "Activate billing policy")
    public Mono<ApiResponse<PolicyResponse>> activatePolicy(
            @PathVariable UUID tenantId, @PathVariable UUID policyId) {
        return billingService.activatePolicy(tenantId, policyId)
                .map(PolicyResponse::from)
                .map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/billing-policies/{policyId}/archive")
    @Operation(summary = "Archive billing policy")
    public Mono<ApiResponse<PolicyResponse>> archivePolicy(
            @PathVariable UUID tenantId, @PathVariable UUID policyId) {
        return billingService.archivePolicy(tenantId, policyId)
                .map(PolicyResponse::from)
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/billing-policies")
    @Operation(summary = "List policies by agency")
    public Mono<ApiResponse<List<PolicyResponse>>> listByAgency(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return billingService.listByAgency(tenantId, agencyId)
                .map(PolicyResponse::from)
                .collectList()
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/billing-policies/{policyId}")
    @Operation(summary = "Get billing policy")
    public Mono<ApiResponse<PolicyResponse>> getPolicy(
            @PathVariable UUID tenantId, @PathVariable UUID policyId) {
        return billingService.getPolicy(tenantId, policyId)
                .map(PolicyResponse::from)
                .map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/billing/estimate")
    @Operation(summary = "Estimate billing")
    public Mono<ApiResponse<BillingService.EstimateResult>> estimate(
            @PathVariable UUID tenantId,
            @RequestBody EstimateRequest body) {
        return billingService.estimate(tenantId, body.agencyId(), body.distanceKm(), body.weightKg())
                .map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/missions/{missionId}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate mission invoice")
    public Mono<ApiResponse<InvoiceResponse>> generateInvoice(
            @PathVariable UUID tenantId,
            @PathVariable UUID missionId,
            @RequestBody GenerateInvoiceRequest body) {
        return billingService.generateInvoice(tenantId, body.agencyId(), missionId)
                .map(InvoiceResponse::from)
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/invoices")
    @Operation(summary = "List agency invoices")
    public Mono<ApiResponse<List<InvoiceResponse>>> listInvoices(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return billingService.listInvoices(tenantId, agencyId)
                .map(InvoiceResponse::from)
                .collectList()
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/invoices/{invoiceId}")
    @Operation(summary = "Get invoice")
    public Mono<ApiResponse<InvoiceResponse>> getInvoice(
            @PathVariable UUID tenantId, @PathVariable UUID invoiceId) {
        return billingService.getInvoice(tenantId, invoiceId)
                .map(InvoiceResponse::from)
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/invoices/{invoiceId}/download")
    @Operation(summary = "Download invoice URL")
    public Mono<ApiResponse<BillingService.InvoiceDownloadResult>> downloadInvoice(
            @PathVariable UUID tenantId, @PathVariable UUID invoiceId) {
        return billingService.downloadInvoice(tenantId, invoiceId)
                .map(ApiResponse::success);
    }

    public record CreatePolicyRequest(
            String name, String description, String currency,
            BigDecimal basePrice, BigDecimal pricePerKm, BigDecimal pricePerKg, BigDecimal minPrice
    ) {}

    public record EstimateRequest(UUID agencyId, double distanceKm, double weightKg) {}

    public record GenerateInvoiceRequest(UUID agencyId) {}

    public record PolicyResponse(
            UUID id, UUID tenantId, UUID agencyId, String name, String description,
            String status, String currency, BigDecimal basePrice, BigDecimal pricePerKm,
            BigDecimal pricePerKg, BigDecimal minPrice
    ) {
        public static PolicyResponse from(BillingPolicy policy) {
            return new PolicyResponse(
                    policy.getId(), policy.getTenantId(), policy.getAgencyId(),
                    policy.getName(), policy.getDescription(), policy.getStatus().name(),
                    policy.getCurrency(), policy.getBasePrice(), policy.getPricePerKm(),
                    policy.getPricePerKg(), policy.getMinPrice()
            );
        }
    }

    public record InvoiceResponse(
            UUID id, UUID tenantId, UUID agencyId, UUID missionId,
            String reference, BigDecimal amount, String currency, String status
    ) {
        public static InvoiceResponse from(InvoiceRecord invoice) {
            return new InvoiceResponse(
                    invoice.getId(), invoice.getTenantId(), invoice.getAgencyId(),
                    invoice.getMissionId(), invoice.getReference(), invoice.getAmount(),
                    invoice.getCurrency(), invoice.getStatus().name()
            );
        }
    }
}
