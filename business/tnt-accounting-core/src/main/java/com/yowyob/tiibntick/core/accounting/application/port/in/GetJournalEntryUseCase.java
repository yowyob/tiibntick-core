package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.JournalEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Use case — Retrieve and list journal entries.
 * @author MANFOUO Braun
 */
public interface GetJournalEntryUseCase {
    Mono<JournalEntry> getJournalEntry(UUID tenantId, UUID journalEntryId);
    Flux<JournalEntry> listJournalEntries(UUID tenantId, UUID organizationId, YearMonth period);
}
