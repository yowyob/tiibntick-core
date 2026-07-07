package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command to credit a commission to a deliverer's wallet after a successful payment.
 * @author MANFOUO Braun
 */
public record CreditCommissionCommand(
        @NotNull UUID delivererId,
        @NotNull UUID tenantId,
        @NotNull Money commissionAmount,
        @NotBlank String missionId,
        @NotBlank String invoiceId
) {}
