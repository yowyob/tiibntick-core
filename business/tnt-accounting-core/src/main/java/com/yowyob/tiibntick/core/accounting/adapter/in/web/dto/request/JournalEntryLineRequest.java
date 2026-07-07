package com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a journal entry request.
 * Author: MANFOUO Braun
 */
public record JournalEntryLineRequest(
        UUID accountId,
        @NotBlank String accountCode,
        @NotBlank String label,
        @PositiveOrZero BigDecimal debitAmount,
        @PositiveOrZero BigDecimal creditAmount,
        @NotBlank String currency
) {}
