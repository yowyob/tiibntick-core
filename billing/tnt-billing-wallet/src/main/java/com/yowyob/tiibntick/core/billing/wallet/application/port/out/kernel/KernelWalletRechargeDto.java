package com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirrors the Kernel's {@code WalletRechargeResponse} schema ({@code payment-controller},
 * {@code POST /api/payments/wallets/{walletId}/recharge} and the recharge-order read/refresh
 * endpoints). Confirmed field-for-field against {@code docs/kernel-api/openapi.json}.
 *
 * <p>{@code status} is a documented enum: {@code PENDING_PAYMENT}, {@code RECHARGED},
 * {@code FAILED}, {@code CANCELLED}, {@code EXPIRED}. {@code redirectUrl} is set for
 * redirect-based flows (card / hosted Mobile Money page).
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelWalletRechargeDto(
        UUID orderId,
        UUID walletId,
        UUID paymentOrderId,
        UUID transactionId,
        String status,
        BigDecimal amount,
        String currency,
        String redirectUrl,
        String providerReference,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isTerminal() {
        return "RECHARGED".equals(status) || "FAILED".equals(status)
                || "CANCELLED".equals(status) || "EXPIRED".equals(status);
    }
}
