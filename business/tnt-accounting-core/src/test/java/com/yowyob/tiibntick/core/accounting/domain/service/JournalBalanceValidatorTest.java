package com.yowyob.tiibntick.core.accounting.domain.service;

import com.yowyob.tiibntick.core.accounting.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JournalBalanceValidator domain service.
 * Author: MANFOUO Braun
 */
class JournalBalanceValidatorTest {

    private JournalBalanceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JournalBalanceValidator();
    }

    @Test
    void should_pass_when_entry_is_balanced() {
        JournalEntry entry = buildEntry(
                JournalEntryLine.debit(1, UUID.randomUUID(), "411000", "Client AR", new BigDecimal("10000"), "XAF"),
                JournalEntryLine.credit(2, UUID.randomUUID(), "704000", "Revenue", new BigDecimal("10000"), "XAF")
        );
        JournalBalanceValidator.ValidationResult result = validator.validate(entry);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void should_fail_when_entry_is_not_balanced() {
        JournalEntry entry = buildEntry(
                JournalEntryLine.debit(1, UUID.randomUUID(), "411000", "Client AR", new BigDecimal("10000"), "XAF"),
                JournalEntryLine.credit(2, UUID.randomUUID(), "704000", "Revenue", new BigDecimal("9000"), "XAF")
        );
        JournalBalanceValidator.ValidationResult result = validator.validate(entry);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("not balanced"));
    }

    @Test
    void should_fail_when_only_one_line() {
        JournalEntry entry = buildEntry(
                JournalEntryLine.debit(1, UUID.randomUUID(), "411000", "Client AR", new BigDecimal("10000"), "XAF")
        );
        JournalBalanceValidator.ValidationResult result = validator.validate(entry);
        assertThat(result.valid()).isFalse();
    }

    @Test
    void should_fail_on_empty_lines() {
        JournalEntry entry = JournalEntry.create(UUID.randomUUID(), UUID.randomUUID(),
                JournalNumber.generate("CMR", 1L), JournalType.GENERAL,
                null, null, List.of(), "test", "user1");
        JournalBalanceValidator.ValidationResult result = validator.validate(entry);
        assertThat(result.valid()).isFalse();
    }

    @Test
    void should_throw_when_validateOrThrow_on_invalid_entry() {
        JournalEntry entry = buildEntry(
                JournalEntryLine.debit(1, UUID.randomUUID(), "411000", "Client AR", new BigDecimal("10000"), "XAF"),
                JournalEntryLine.credit(2, UUID.randomUUID(), "704000", "Revenue", new BigDecimal("5000"), "XAF")
        );
        assertThatThrownBy(() -> validator.validateOrThrow(entry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not balanced");
    }

    private JournalEntry buildEntry(JournalEntryLine... lines) {
        return JournalEntry.create(UUID.randomUUID(), UUID.randomUUID(),
                JournalNumber.generate("CMR", 1L), JournalType.SALES,
                "INVOICE", UUID.randomUUID().toString(),
                List.of(lines), "Test entry", "user1");
    }
}
