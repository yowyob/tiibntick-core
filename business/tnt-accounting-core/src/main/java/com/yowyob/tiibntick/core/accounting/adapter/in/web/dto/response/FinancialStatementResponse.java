package com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.accounting.domain.model.FinancialLine;
import com.yowyob.tiibntick.core.accounting.domain.model.FinancialStatement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API response DTO for a financial statement (Trial Balance, P&amp;L, Balance Sheet).
 * Author: MANFOUO Braun
 */
public record FinancialStatementResponse(
        UUID id,
        UUID tenantId,
        String type,
        String period,
        String currency,
        List<FinancialLineResponse> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal netResult,
        boolean balanced,
        Instant generatedAt
) {
    public static FinancialStatementResponse from(FinancialStatement s) {
        return new FinancialStatementResponse(
                s.id(), s.tenantId(), s.type().name(), s.period().toString(), s.currency(),
                s.lines().stream().map(FinancialLineResponse::from).toList(),
                s.totalDebit(), s.totalCredit(), s.netResult(), s.isBalanced(), s.generatedAt());
    }

    public record FinancialLineResponse(String lineCode, String label, BigDecimal amount, String section) {
        public static FinancialLineResponse from(FinancialLine l) {
            return new FinancialLineResponse(l.lineCode(), l.label(), l.amount(), l.section());
        }
    }
}
