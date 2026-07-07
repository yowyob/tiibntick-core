package com.yowyob.tiibntick.core.accounting.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A single line in a financial statement (Balance Sheet or P&amp;L).
 * Author: MANFOUO Braun
 */
public record FinancialLine(
        String lineCode,
        String label,
        BigDecimal amount,
        String section
) {

    public FinancialLine {
        Objects.requireNonNull(lineCode);
        Objects.requireNonNull(label);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(section);
    }
}
