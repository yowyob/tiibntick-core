package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command for creating a new account in the chart of accounts.
 * Author: MANFOUO Braun
 */
public record CreateAccountCommand(
        @NotNull UUID tenantId,
        @NotNull UUID organizationId,
        @NotBlank String code,
        @NotBlank String name,
        @NotNull AccountType type,
        @NotBlank String currency,
        UUID parentAccountId
) {}
