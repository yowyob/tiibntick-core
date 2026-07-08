package com.yowyob.tiibntick.core.accounting.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 * <p>Field names below are aliased with {@link JsonProperty} to the Kernel's real
 * {@code InvoiceResponse} JSON keys ({@code id}, {@code customerThirdPartyId}) — see
 * {@code docs/kernel-api/schemas.md}. {@code issuedAt} has no Kernel-side equivalent
 * ({@code InvoiceResponse} carries no date field) — always {@code null}.</p>
 *
 * @param kernelInvoiceId   UUID of the invoice in the Kernel database
 * @param tenantId          tenant owning this invoice in the Kernel
 * @param organizationId    Kernel organization UUID
 * @param clientThirdPartyId Kernel third-party UUID of the invoiced client
 * @param invoiceNumber     human-readable invoice reference (e.g. INV-2026-000042)
 * @param totalAmount       gross invoice amount
 * @param currency          ISO 4217 currency code (e.g. XAF)
 * @param status            invoice status as text (DRAFT, SENT, PAID, CANCELLED)
 * @param issuedAt          no Kernel equivalent — always {@code null}
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelInvoiceDto(
        @JsonProperty("id") UUID kernelInvoiceId,
        UUID tenantId,
        UUID organizationId,
        @JsonProperty("customerThirdPartyId") UUID clientThirdPartyId,
        String invoiceNumber,
        BigDecimal totalAmount,
        String currency,
        String status,
        Instant issuedAt
) {}
