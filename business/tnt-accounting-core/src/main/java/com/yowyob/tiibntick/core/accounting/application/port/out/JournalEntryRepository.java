package com.yowyob.tiibntick.core.accounting.application.port.out;

import com.yowyob.tiibntick.core.accounting.domain.model.JournalEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.UUID;

/** Outbound port for JournalEntry persistence. Author: MANFOUO Braun */
public interface JournalEntryRepository {
    Mono<JournalEntry> save(JournalEntry entry);
    Mono<JournalEntry> findById(UUID tenantId, UUID journalEntryId);
    Flux<JournalEntry> findByTenantIdAndOrganizationIdAndPeriod(UUID tenantId, UUID organizationId, YearMonth period);
    Flux<JournalEntry> findByReferenceId(UUID tenantId, String referenceId);
    Mono<Boolean> existsByNumber(UUID tenantId, String journalNumber);
}
