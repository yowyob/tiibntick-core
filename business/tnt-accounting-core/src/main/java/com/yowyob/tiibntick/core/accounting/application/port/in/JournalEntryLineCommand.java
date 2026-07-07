package com.yowyob.tiibntick.core.accounting.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command representing one line of a journal entry.
 *
 * <p>Either {@code debitAmount} or {@code creditAmount} must be positive;
 * the other must be zero (enforced by domain validation in {@code JournalEntryLine}).</p>
 *
 * <p>Note: {@code accountId} is optional — entries generated from Kafka billing events
 * may not have the TNT account UUID at command creation time. The application service
 * resolves the UUID from {@code accountCode} before persisting.</p>
 *
 * @author MANFOUO Braun
 */
public record JournalEntryLineCommand(
        UUID accountId,              // nullable — resolved from accountCode if null
        @NotBlank String accountCode,
        @NotBlank String label,
        @NotNull @PositiveOrZero BigDecimal debitAmount,
        @NotNull @PositiveOrZero BigDecimal creditAmount,
        @NotBlank String currency
) {}
