package com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.accounting.domain.model.JournalEntryLine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * API response DTO for a single JournalEntryLine.
 * Author: MANFOUO Braun
 */
public record JournalEntryLineResponse(
        int lineNumber,
        UUID accountId,
        String accountCode,
        String label,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String currency
) {
    public static JournalEntryLineResponse from(JournalEntryLine l) {
        return new JournalEntryLineResponse(l.lineNumber(), l.accountId(), l.accountCode(),
                l.label(), l.debitAmount(), l.creditAmount(), l.currency());
    }
}
