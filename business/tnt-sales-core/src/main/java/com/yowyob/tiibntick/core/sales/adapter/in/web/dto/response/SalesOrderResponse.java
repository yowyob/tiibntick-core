package com.yowyob.tiibntick.core.sales.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.sales.domain.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API response DTO for a TiiBnTick SalesOrder.
 * Includes the optional {@code kernelSalesOrderId} for Kernel-linked orders.
 *
 * @author MANFOUO Braun
 */
public record SalesOrderResponse(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID agencyId,
        UUID clientThirdPartyId,
        String orderNumber,
        String status,
        String priority,
        String paymentStatus,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        UUID missionId,
        UUID invoiceId,
        /** Nullable — set only when the order is linked to a Kernel sales record. */
        UUID kernelSalesOrderId,
        String returnReason,
        String returnNote,
        String cancelReason,
        List<SalesOrderLineResponse> lines,
        TntAddressResponse deliveryAddress,
        TntAddressResponse billingAddress,
        Instant confirmedAt,
        Instant deliveredAt,
        Instant returnedAt,
        Instant createdAt,
        Instant updatedAt,
        /** Type of provider: AGENCY | FREELANCER_ORG. Null for standard agency orders. */
        String providerOrgType,
        /** UUID of the executing org (FreelancerOrg or Agency). Nullable. */
        String providerOrgId
) {
    public static SalesOrderResponse from(TntSalesOrder o) {
        return new SalesOrderResponse(
                o.getId(), o.getTenantId(), o.getOrganizationId(), o.getAgencyId(),
                o.getClientThirdPartyId(), o.getOrderNumber(),
                o.getStatus().name(), o.getPriority().name(), o.getPaymentStatus().name(),
                o.getCurrency(), o.getSubtotalAmount(), o.getTotalAmount(),
                o.getMissionId(), o.getInvoiceId(),
                o.getKernelSalesOrderId(), // nullable — exposed for traceability
                o.getReturnReason() != null ? o.getReturnReason().name() : null,
                o.getReturnNote(), o.getCancelReason(),
                o.getLines().stream().map(SalesOrderLineResponse::from).toList(),
                TntAddressResponse.from(o.getDeliveryAddress()),
                o.getBillingAddress() != null ? TntAddressResponse.from(o.getBillingAddress()) : null,
                o.getConfirmedAt(), o.getDeliveredAt(), o.getReturnedAt(),
                o.getCreatedAt(), o.getUpdatedAt(),
                o.getProviderOrgType(), o.getProviderOrgId() // 
        );
    }
}
