package com.yowyob.tiibntick.core.accounting.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a journal entry is definitively posted to the ledger.
 * Author: MANFOUO Braun
 */
public record JournalEntryPostedEvent(
        UUID journalEntryId,
        UUID tenantId,
        UUID organizationId,
        String journalNumber,
        String journalType,
        String referenceType,
        String referenceId,
        java.math.BigDecimal amount,
        Instant occurredAt
) {

    public static JournalEntryPostedEvent of(
            com.yowyob.tiibntick.core.accounting.domain.model.JournalEntry entry) {
        return new JournalEntryPostedEvent(
                entry.getId(), entry.getTenantId(), entry.getOrganizationId(),
                entry.getNumber().value(), entry.getType().name(),
                entry.getReferenceType(), entry.getReferenceId(),
                entry.debitTotal(), Instant.now());
    }
}
