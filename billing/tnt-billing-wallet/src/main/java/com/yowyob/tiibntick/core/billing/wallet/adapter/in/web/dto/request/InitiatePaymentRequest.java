package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.request;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * REST request DTO to initiate a payment via Mobile Money or Stripe.
 * @author MANFOUO Braun
 */
public record InitiatePaymentRequest(
        @NotBlank String invoiceId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotNull PaymentChannel channel,
        String payerPhone,
        String callbackUrl,
        String description
) {}
