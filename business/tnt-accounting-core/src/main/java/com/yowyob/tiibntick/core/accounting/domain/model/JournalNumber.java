package com.yowyob.tiibntick.core.accounting.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Strongly-typed value object representing a journal entry reference number.
 * Format: TNT-JNL-{TENANT_CODE}-{YEAR}-{SEQUENCE}
 * Example: TNT-JNL-CMR-2026-000001
 * Author: MANFOUO Braun
 */
public record JournalNumber(String value) {

    public JournalNumber {
        Objects.requireNonNull(value, "JournalNumber value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("JournalNumber value must not be blank");
        }
    }

    /**
     * Generates a new JournalNumber for a given tenant and sequence.
     *
     * @param tenantCode short tenant code (e.g. "CMR", "NG")
     * @param sequence   monotonically increasing counter, reset per year
     * @return formatted JournalNumber
     */
    public static JournalNumber generate(String tenantCode, long sequence) {
        Objects.requireNonNull(tenantCode, "tenantCode is required");
        if (tenantCode.isBlank()) {
            throw new IllegalArgumentException("tenantCode must not be blank");
        }
        int year = LocalDate.now().getYear();
        String formatted = String.format("TNT-JNL-%s-%d-%06d",
                tenantCode.toUpperCase(), year, sequence);
        return new JournalNumber(formatted);
    }

    public static JournalNumber of(String raw) {
        return new JournalNumber(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
