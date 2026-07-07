package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to refund a previously confirmed payment.
 * @author MANFOUO Braun
 */
public record RefundPaymentCommand(
        @NotNull UUID paymentIntentId,
        @NotNull UUID tenantId,
        @NotBlank String reason
) {}
