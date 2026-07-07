package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.mapper;

import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.dto.request.GenerateInvoiceRequest;
import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.dto.response.InvoiceResponse;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Mapper for Invoice REST layer (request → command and domain → response).
 *
 * <p> — Propagates FreelancerOrg issuer context and template metadata.
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoiceWebMapper {

    /**
     * Converts an Invoice domain object to an HTTP response DTO.
     */
    public InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getNumber().value(),
                invoice.getTenantId(),
                invoice.getCountryCode(),
                invoice.getMissionId(),
                invoice.getSalesOrderId(),
                invoice.getClientId(),
                invoice.getLines(),
                invoice.getSubtotalExTax(),
                invoice.getTaxLines(),
                invoice.getTotalTax(),
                invoice.getTotalIncTax(),
                invoice.getDiscounts(),
                invoice.getNetAmount(),
                invoice.getStatus(),
                invoice.getPdfStorageKey(),
                invoice.getIssuedAt(),
                invoice.getDueAt(),
                invoice.getPaidAt(),
                invoice.getCancelledAt(),
                invoice.getCancellationReason(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                //  fields
                invoice.getIssuerOrgType(),
                invoice.getIssuerOrgId(),
                invoice.getIssuerTradeName(),
                invoice.getVatApplicable(),
                invoice.getSurchargeLines(),
                invoice.getIsFromTemplate(),
                invoice.getAppliedTemplateName());
    }

    /**
     * Converts an HTTP request to a GenerateInvoiceCommand.
     *
     * @param tenantId the tenant UUID from the path variable
     * @param request  the HTTP request body
     * @return the application command
     */
    public GenerateInvoiceCommand toCommand(UUID tenantId, GenerateInvoiceRequest request) {
        return new GenerateInvoiceCommand(
                tenantId,
                request.tenantCode(),
                request.countryCode(),
                request.missionId(),
                request.salesOrderId(),
                request.clientId(),
                request.lines(),
                request.discounts(),
                request.currency(),
                request.dueAt(),
                //  fields
                request.issuerOrgType(),
                request.issuerOrgId(),
                request.issuerTradeName(),
                request.vatApplicable(),
                request.surchargeLines() != null ? request.surchargeLines() : List.of(),
                request.isFromTemplate(),
                request.appliedTemplateName());
    }
}
