package com.yowyob.tiibntick.core.accounting.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A single debit or credit line within a JournalEntry.
 * Either debitAmount or creditAmount must be positive; the other must be ZERO.
 * Author: MANFOUO Braun
 */
public record JournalEntryLine(
        int lineNumber,
        UUID accountId,
        String accountCode,
        String label,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String currency
) {

    public JournalEntryLine {
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(accountCode, "accountCode is required");
        if (accountCode.isBlank()) {
            throw new IllegalArgumentException("accountCode must not be blank");
        }
        Objects.requireNonNull(label, "label is required");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        Objects.requireNonNull(debitAmount, "debitAmount is required");
        Objects.requireNonNull(creditAmount, "creditAmount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (debitAmount.signum() < 0) {
            throw new IllegalArgumentException("debitAmount must not be negative");
        }
        if (creditAmount.signum() < 0) {
            throw new IllegalArgumentException("creditAmount must not be negative");
        }
        if (debitAmount.signum() == 0 && creditAmount.signum() == 0) {
            throw new IllegalArgumentException("Either debitAmount or creditAmount must be positive");
        }
        if (debitAmount.signum() > 0 && creditAmount.signum() > 0) {
            throw new IllegalArgumentException("A line cannot be both debit and credit simultaneously");
        }
    }

    public static JournalEntryLine debit(int lineNumber, UUID accountId, String accountCode,
                                         String label, BigDecimal amount, String currency) {
        return new JournalEntryLine(lineNumber, accountId, accountCode, label, amount, BigDecimal.ZERO, currency);
    }

    public static JournalEntryLine credit(int lineNumber, UUID accountId, String accountCode,
                                          String label, BigDecimal amount, String currency) {
        return new JournalEntryLine(lineNumber, accountId, accountCode, label, BigDecimal.ZERO, amount, currency);
    }

    public boolean isDebit() {
        return debitAmount.signum() > 0;
    }

    public boolean isCredit() {
        return creditAmount.signum() > 0;
    }

    public BigDecimal netAmount() {
        return isDebit() ? debitAmount : creditAmount;
    }
}
