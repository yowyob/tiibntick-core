package com.yowyob.tiibntick.core.sales.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only DTO representing a sales order fetched from the Yowyob Kernel
 * (RT-comops-sales-core) via the {@link com.yowyob.tiibntick.core.sales.application.port.out.KernelSalesOrderPort}.
 *
 * <p>This record is <b>never persisted</b> in tnt_core_db. It is used exclusively
 * for enrichment, existence validation, and optional back-reference at query time.</p>
 *
 * <p>Integration pattern: optional coupling — a TiiBnTick SalesOrder can exist without
 * a Kernel counterpart (informal transactions, cash-on-delivery in rural areas).</p>
 *
 * <p>Field names below are aliased with {@link JsonProperty} to the Kernel's real
 * {@code SalesOrderResponse} JSON keys ({@code id}, {@code customerThirdPartyId}) — see
 * {@code docs/kernel-api/schemas.md}. {@code isActive} has no Kernel-side equivalent
 * (the Kernel models order lifecycle via {@code status}, not a boolean flag) — always
 * {@code false} and currently unused by any caller.</p>
 *
 * @param kernelSalesOrderId UUID of the sales order in the Kernel database
 * @param tenantId           tenant owning this sales order in the Kernel
 * @param clientThirdPartyId Kernel third-party UUID of the client
 * @param organizationId     Kernel organization UUID
 * @param totalAmount        Total amount as recorded in the Kernel
 * @param currency           ISO 4217 currency code (e.g. XAF, NGN)
 * @param status             Order status as text from the Kernel lifecycle
 * @param isActive           No Kernel equivalent — always {@code false}, kept for API stability
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelSalesOrderDto(
        @JsonProperty("id") UUID kernelSalesOrderId,
        UUID tenantId,
        @JsonProperty("customerThirdPartyId") UUID clientThirdPartyId,
        UUID organizationId,
        BigDecimal totalAmount,
        String currency,
        String status,
        boolean isActive
) {}
