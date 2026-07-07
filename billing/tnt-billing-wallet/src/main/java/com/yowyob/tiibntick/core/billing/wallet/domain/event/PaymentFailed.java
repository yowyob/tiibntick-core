package com.yowyob.tiibntick.core.billing.wallet.domain.event;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event — emitted when a payment provider returns a failure callback.
 * @author MANFOUO Braun
 */
public record PaymentFailed(
        UUID paymentIntentId,
        UUID walletId,
        UUID userId,
        UUID tenantId,
        String invoiceId,
        Money amount,
        PaymentChannel channel,
        String failureReason,
        LocalDateTime occurredAt
) {
    public PaymentFailed(UUID paymentIntentId, UUID walletId, UUID userId, UUID tenantId,
                         String invoiceId, Money amount, PaymentChannel channel, String failureReason) {
        this(paymentIntentId, walletId, userId, tenantId, invoiceId, amount, channel, failureReason, LocalDateTime.now());
    }
}
