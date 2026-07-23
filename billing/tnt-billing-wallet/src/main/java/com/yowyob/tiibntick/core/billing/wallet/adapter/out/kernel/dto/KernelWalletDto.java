package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirrors the Kernel's {@code WalletResponse} schema
 * ({@code payment-controller}, {@code docs/kernel-api/schemas.md}).
 *
 * <p>Confirmed field-for-field against the published OpenAPI spec (unlike
 * {@link KernelWalletTransactionDto} — see that class's javadoc for the caveat
 * on the pay/recharge/transactions schemas).
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelWalletDto(
        UUID id,
        UUID ownerId,
        String ownerName,
        BigDecimal balance,
        Instant createdAt,
        Instant updatedAt
) {
}
