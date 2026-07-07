package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Command to confirm a payment after webhook callback from the provider.
 * @author MANFOUO Braun
 */
public record ConfirmPaymentCommand(
        /** Provider's own reference ID (MTN referenceId, Stripe paymentIntentId). */
        @NotBlank String externalRef,
        /** Provider's financial transaction ID. */
        @NotBlank String financialTransactionId,
        @NotNull PaymentChannel channel,
        /** Final status reported by the provider: SUCCESSFUL or FAILED. */
        @NotBlank String providerStatus,
        String failureReason
) {}
