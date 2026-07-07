package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.*;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response for Invoice.
 *
 * <p> — Added FreelancerOrg issuer context and template metadata fields.
 *
 * @author MANFOUO Braun
 */
public record InvoiceResponse(
        UUID id,
        String number,
        UUID tenantId,
        String countryCode,
        String missionId,
        String salesOrderId,
        String clientId,
        List<InvoiceLine> lines,
        Money subtotalExTax,
        List<TaxLine> taxLines,
        Money totalTax,
        Money totalIncTax,
        List<InvoiceDiscount> discounts,
        Money netAmount,
        InvoiceStatus status,
        String pdfStorageKey,
        LocalDateTime issuedAt,
        LocalDateTime dueAt,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,

        // : FreelancerOrg issuer context
        /** Type of the issuing organization: AGENCY | FREELANCER_ORG | POINT | LINK */
        String issuerOrgType,
        /** UUID of the issuing organization. */
        String issuerOrgId,
        /** Commercial trade name displayed on the invoice. */
        String issuerTradeName,
        /** Whether TVA/VAT is applicable for this invoice. */
        Boolean vatApplicable,
        /** Surcharge detail lines from the billing DSL. */
        List<SurchargeLineItem> surchargeLines,
        /** Whether this invoice was generated from a billing policy template. */
        Boolean isFromTemplate,
        /** Name of the applied billing policy template. */
        String appliedTemplateName
) {}
