package com.yowyob.tiibntick.core.accounting.application.port.out;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes the books in the Kernel accounting service ({@code /api/accounting-service/*}, lot L6, cible §4.6). Every call
 * is made for one organisation (the book's owner), sent as {@code X-Organization-Id} (K7). Platform identity: the
 * Core's service account, without transport retry (a timed-out POST may have created the entry).
 */
public interface KernelBookkeepingPort {

    /** Account {@code number} of the organisation: searched, created when absent. */
    Mono<UUID> findOrCreateAccount(UUID organizationId, String number, String label, String type, UUID externalId);

    /** Journal {@code code} of the organisation: listed, created when absent. */
    Mono<UUID> findOrCreateJournal(UUID organizationId, String code, String label, String type);

    /** {@code POST /entries}. */
    Mono<KernelEntry> postEntry(UUID organizationId, UUID journalId, String reference, Instant date,
                                List<KernelLine> lines);

    /** {@code POST /entries/{id}/validate}. */
    Mono<KernelEntry> validateEntry(UUID organizationId, UUID entryId);

    /** An entry of the organisation with this reference (recovery after a crash between the POST and its record). */
    Mono<KernelEntry> findEntryByReference(UUID organizationId, String reference);

    record KernelLine(UUID accountId, BigDecimal debit, BigDecimal credit, String label) {
    }

    record KernelEntry(UUID id, String status) {
    }
}
