package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to synchronously debit a wallet (e.g. wallet-to-wallet transfer).
 * @author MANFOUO Braun
 */
public record DebitWalletCommand(
        @NotNull UUID userId,
        @NotNull UUID tenantId,
        @NotNull Money amount,
        @NotBlank String referenceId,
        @NotNull PaymentChannel channel,
        String description,
        @NotBlank String idempotencyKey
) {}
