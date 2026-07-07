package com.yowyob.tiibntick.core.accounting.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Generated financial statement snapshot (Balance Sheet, P&amp;L, etc.).
 * Author: MANFOUO Braun
 */
public record FinancialStatement(
        UUID id,
        UUID tenantId,
        StatementType type,
        YearMonth period,
        String currency,
        List<FinancialLine> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        Instant generatedAt
) {

    public FinancialStatement {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(period);
        Objects.requireNonNull(currency);
        lines = Collections.unmodifiableList(Objects.requireNonNull(lines));
        Objects.requireNonNull(totalDebit);
        Objects.requireNonNull(totalCredit);
        Objects.requireNonNull(generatedAt);
    }

    public static FinancialStatement generate(UUID tenantId, StatementType type, YearMonth period,
                                               String currency, List<FinancialLine> lines,
                                               BigDecimal totalDebit, BigDecimal totalCredit) {
        return new FinancialStatement(UUID.randomUUID(), tenantId, type, period, currency,
                lines, totalDebit, totalCredit, Instant.now());
    }

    public BigDecimal netResult() {
        return totalCredit.subtract(totalDebit);
    }

    public boolean isBalanced() {
        return totalDebit.compareTo(totalCredit) == 0;
    }
}
