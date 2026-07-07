package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * R2DBC entity mapped to the accounting.journal_entry_lines table.
 * Author: MANFOUO Braun
 */
@Table(schema = "accounting", name = "journal_entry_lines")
public record JournalEntryLineEntity(
        @Id @Column("id") UUID id,
        @Column("journal_entry_id") UUID journalEntryId,
        @Column("line_number") int lineNumber,
        @Column("account_id") UUID accountId,
        @Column("account_code") String accountCode,
        @Column("label") String label,
        @Column("debit_amount") BigDecimal debitAmount,
        @Column("credit_amount") BigDecimal creditAmount,
        @Column("currency") String currency
) {}
