package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import java.util.Objects;

/**
 * Value Object: InvoiceNumber.
 *
 * <p>Format: {@code TNT-FACT-{TENANT_CODE}-{YEAR}-{SEQ:06d}}
 * Example: {@code TNT-FACT-AGY001-2026-000042}
 *
 * <p>The sequence is per-tenant per-year, generated atomically via the
 * comops-settings-core document sequence service.</p>
 *
 * @author MANFOUO Braun
 */
public record InvoiceNumber(String value) {

    public InvoiceNumber {
        Objects.requireNonNull(value, "Invoice number value is required");
        if (!value.startsWith("TNT-FACT-")) {
            throw new IllegalArgumentException("Invalid invoice number format: " + value);
        }
    }

    /**
     * Generates a formatted invoice number from its components.
     *
     * @param tenantCode short tenant identifier (e.g. "AGY001")
     * @param year       the year of issuance
     * @param seq        the sequential number within the tenant-year scope
     * @return a valid InvoiceNumber
     */
    public static InvoiceNumber generate(String tenantCode, int year, long seq) {
        Objects.requireNonNull(tenantCode, "tenantCode is required");
        String formatted = String.format("TNT-FACT-%s-%d-%06d", tenantCode.toUpperCase(), year, seq);
        return new InvoiceNumber(formatted);
    }

    @Override
    public String toString() {
        return value;
    }
}
