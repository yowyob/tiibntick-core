package com.yowyob.tiibntick.core.accounting.domain.service;

import com.yowyob.tiibntick.core.accounting.domain.model.JournalEntry;
import com.yowyob.tiibntick.core.accounting.domain.model.JournalEntryLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service that validates double-entry balance rules for a JournalEntry.
 * A valid entry must have:
 *  1. At least 2 lines.
 *  2. Sum of debits == sum of credits.
 *  3. All amounts strictly positive (no zero-amount lines).
 *  4. No line that is simultaneously debit and credit.
 * Author: MANFOUO Braun
 */
@Component
public class JournalBalanceValidator {

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }
    }

    public ValidationResult validate(JournalEntry entry) {
        List<String> errors = new ArrayList<>();

        if (entry.getLines().isEmpty()) {
            errors.add("Journal entry must have at least one line");
            return ValidationResult.fail(errors);
        }

        if (entry.getLines().size() < 2) {
            errors.add("Journal entry must have at least 2 lines (one debit and one credit)");
        }

        BigDecimal totalDebit = entry.debitTotal();
        BigDecimal totalCredit = entry.creditTotal();

        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Total debit amount must be strictly positive");
        }

        if (totalCredit.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Total credit amount must be strictly positive");
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            errors.add(String.format(
                    "Entry is not balanced: total debits [%s] != total credits [%s]",
                    totalDebit.toPlainString(), totalCredit.toPlainString()));
        }

        for (int i = 0; i < entry.getLines().size(); i++) {
            JournalEntryLine line = entry.getLines().get(i);
            if (line.debitAmount().signum() == 0 && line.creditAmount().signum() == 0) {
                errors.add(String.format("Line %d has zero debit and zero credit amount", i + 1));
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    public void validateOrThrow(JournalEntry entry) {
        ValidationResult result = validate(entry);
        if (!result.valid()) {
            throw new IllegalStateException(
                    "Journal entry validation failed: " + String.join("; ", result.errors()));
        }
    }
}
