package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Projection: InvoiceReportEntry.
 *
 * <p>A lightweight read-model projection of invoices consumed by the report engine.
 * Populated by the Kafka consumer listening to InvoiceGenerated / InvoicePaid / InvoiceCancelled events.
 * Stored in tnt_invoice_report_entries table for fast aggregate queries.</p>
 *
 * @author MANFOUO Braun
 */
public record InvoiceReportEntry(
        UUID invoiceId,
        String invoiceNumber,
        UUID tenantId,
        String countryCode,
        String clientId,
        String missionId,
        Money grossAmount,
        Money taxAmount,
        Money netAmount,
        Money platformFee,
        InvoiceStatus status,
        LocalDate invoiceDate,
        LocalDate paidDate,

        // ── : FreelancerOrg and billing template context ─────────────────

        /**
         * Type of the issuing organization: AGENCY | FREELANCER_ORG | POINT | LINK.
         * Null for standard platform-issued invoices.
         */
        String issuerOrgType,

        /**
         * UUID of the issuing organization (FreelancerOrg or Agency).
         * References tnt-organization-core UUID — pure integration key.
         */
        String issuerOrgId,

        /**
         * Name of the billing policy template used to generate this invoice's billing policy.
         * Null when not generated from a template.
         */
        String appliedTemplateName,

        /**
         * Total surcharge amount on this invoice (sum of all DSL surcharges: FRAGILE, NIGHT, etc.)
         */
        Money totalSurchargeAmount
) {}
