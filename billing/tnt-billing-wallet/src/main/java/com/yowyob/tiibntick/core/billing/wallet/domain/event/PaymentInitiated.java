package com.yowyob.tiibntick.core.billing.wallet.domain.event;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event — emitted when a payment intent is created (MoMo USSD push sent).
 * @author MANFOUO Braun
 */
public record PaymentInitiated(
        UUID paymentIntentId,
        UUID walletId,
        UUID userId,
        UUID tenantId,
        String invoiceId,
        Money amount,
        PaymentChannel channel,
        String payerPhone,
        LocalDateTime occurredAt
) {
    public PaymentInitiated(UUID paymentIntentId, UUID walletId, UUID userId, UUID tenantId,
                            String invoiceId, Money amount, PaymentChannel channel, String payerPhone) {
        this(paymentIntentId, walletId, userId, tenantId, invoiceId, amount, channel, payerPhone, LocalDateTime.now());
    }
}
