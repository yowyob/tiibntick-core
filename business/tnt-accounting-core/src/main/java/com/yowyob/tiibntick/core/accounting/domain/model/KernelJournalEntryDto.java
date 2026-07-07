package com.yowyob.tiibntick.core.accounting.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only DTO representing a journal entry fetched from the Yowyob Kernel
 * (RT-comops-accounting-core) via {@link com.yowyob.tiibntick.core.accounting.application.port.out.KernelAccountingPort}.
 *
 * <p>This record is <b>never persisted</b> in tnt_core_db. It provides an optional
 * back-reference to the Kernel ledger for journal entries posted in TiiBnTick that
 * have a counterpart in the Kernel ERP (e.g. synced via Kafka).</p>
 *
 * @param kernelJournalEntryId UUID of the journal entry in the Kernel database
 * @param tenantId             tenant owning this entry in the Kernel
 * @param organizationId       Kernel organization UUID
 * @param journalNumber        Kernel journal reference number
 * @param entryType            type of journal (SALES, BANK, MISC, etc.)
 * @param debitTotal           total debited amount
 * @param creditTotal          total credited amount
 * @param currency             ISO 4217 currency code
 * @param status               entry status (DRAFT, POSTED)
 * @param postedAt             date/time the entry was posted in the Kernel
 *
 * @author MANFOUO Braun
 */
public record KernelJournalEntryDto(
        UUID kernelJournalEntryId,
        UUID tenantId,
        UUID organizationId,
        String journalNumber,
        String entryType,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        String currency,
        String status,
        Instant postedAt
) {}
