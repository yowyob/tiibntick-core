package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Pays a dispute compensation from the Kernel compensation fund (spec §4.3). */
public record PayCompensationCommand(UUID tenantId, String disputeId, UUID beneficiaryActorId, BigDecimal amount,
                                     String currency) {

    public PayCompensationCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(disputeId, "disputeId");
        Objects.requireNonNull(beneficiaryActorId, "beneficiaryActorId");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("A compensation amount must be positive");
        }
        currency = currency == null || currency.isBlank() ? "XAF" : currency;
    }
}
