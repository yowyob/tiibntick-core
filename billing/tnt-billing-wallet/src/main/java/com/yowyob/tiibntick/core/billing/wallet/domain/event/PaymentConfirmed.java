package com.yowyob.tiibntick.core.billing.wallet.domain.event;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event — emitted when a MoMo/Stripe webhook confirms successful payment.
 * @author MANFOUO Braun
 */
public record PaymentConfirmed(
        UUID paymentIntentId,
        UUID walletId,
        UUID userId,
        UUID tenantId,
        String invoiceId,
        Money amount,
        PaymentChannel channel,
        String externalRef,
        LocalDateTime occurredAt
) {
    public PaymentConfirmed(UUID paymentIntentId, UUID walletId, UUID userId, UUID tenantId,
                            String invoiceId, Money amount, PaymentChannel channel, String externalRef) {
        this(paymentIntentId, walletId, userId, tenantId, invoiceId, amount, channel, externalRef, LocalDateTime.now());
    }
}
