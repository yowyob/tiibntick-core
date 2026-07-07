package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Command to credit an amount to a user's wallet.
 * @author MANFOUO Braun
 */
public record CreditWalletCommand(
        @NotNull UUID userId,
        @NotNull UUID tenantId,
        @NotNull Money amount,
        @NotBlank String referenceId,
        String description
) {}
