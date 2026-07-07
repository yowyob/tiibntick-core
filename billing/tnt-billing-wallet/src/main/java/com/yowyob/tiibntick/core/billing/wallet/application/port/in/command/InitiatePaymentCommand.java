package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to initiate an asynchronous payment (MTN MoMo, Orange Money, Stripe).
 * @author MANFOUO Braun
 */
public record InitiatePaymentCommand(
        @NotNull UUID userId,
        @NotNull UUID tenantId,
        @NotBlank String invoiceId,
        @NotNull Money amount,
        @NotNull PaymentChannel channel,
        /** Payer's phone number — required for MoMo channels. */
        String payerPhone,
        String callbackUrl,
        String description
) {}
