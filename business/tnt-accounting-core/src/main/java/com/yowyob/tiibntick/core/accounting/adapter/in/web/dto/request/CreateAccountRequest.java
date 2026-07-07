package com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.request;

import com.yowyob.tiibntick.core.accounting.domain.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * HTTP request body for creating an account.
 * Author: MANFOUO Braun
 */
public record CreateAccountRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull AccountType type,
        @NotBlank String currency,
        UUID parentAccountId
) {}
