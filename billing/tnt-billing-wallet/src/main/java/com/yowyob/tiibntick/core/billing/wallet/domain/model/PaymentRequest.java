package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import lombok.Builder;

/**
 * PaymentRequest — value object encapsulating all data needed to initiate a payment.
 *
 * @author MANFOUO Braun
 */
@Builder
public record PaymentRequest(
        String invoiceId,
        Money amount,
        PaymentChannel channel,
        /** Payer's phone number in E.164 format — required for MoMo channels. */
        String payerPhone,
        /**
         * Idempotency key — format: {invoiceId}:{channel}.
         * Prevents double-charges if the client retries.
         */
        String idempotencyKey,
        /** URL to call after payment confirmation (for redirect-based flows). */
        String callbackUrl,
        String description
) {
    public PaymentRequest {
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new IllegalArgumentException("invoiceId must not be blank");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("amount must be a positive Money value");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }

    /**
     * Builds the standard idempotency key for a given invoice + channel combination.
     */
    public static String buildIdempotencyKey(String invoiceId, PaymentChannel channel) {
        return invoiceId + ":" + channel.name();
    }
}
