package com.yowyob.tiibntick.core.accounting.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only DTO representing an invoice fetched from the Yowyob Kernel
 * (RT-comops-accounting-core) via {@link com.yowyob.tiibntick.core.accounting.application.port.out.KernelAccountingPort}.
 *
 * <p>This record is <b>never persisted</b> in tnt_core_db. It is used exclusively
 * for enrichment and optional cross-referencing when a TiiBnTick journal entry
 * is generated from a billing event that has a corresponding Kernel invoice.</p>
 *
 * @param kernelInvoiceId   UUID of the invoice in the Kernel database
 * @param tenantId          tenant owning this invoice in the Kernel
 * @param organizationId    Kernel organization UUID
 * @param clientThirdPartyId Kernel third-party UUID of the invoiced client
 * @param invoiceNumber     human-readable invoice reference (e.g. INV-2026-000042)
 * @param totalAmount       gross invoice amount
 * @param currency          ISO 4217 currency code (e.g. XAF)
 * @param status            invoice status as text (DRAFT, SENT, PAID, CANCELLED)
 * @param issuedAt          invoice emission date
 *
 * @author MANFOUO Braun
 */
public record KernelInvoiceDto(
        UUID kernelInvoiceId,
        UUID tenantId,
        UUID organizationId,
        UUID clientThirdPartyId,
        String invoiceNumber,
        BigDecimal totalAmount,
        String currency,
        String status,
        Instant issuedAt
) {}
