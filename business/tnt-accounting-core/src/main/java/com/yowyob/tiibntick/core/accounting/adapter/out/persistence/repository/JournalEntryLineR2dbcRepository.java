package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity.JournalEntryLineEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for JournalEntryLine entities.
 * Author: MANFOUO Braun
 */
public interface JournalEntryLineR2dbcRepository extends ReactiveCrudRepository<JournalEntryLineEntity, UUID> {

    @Query("SELECT * FROM accounting.journal_entry_lines WHERE journal_entry_id = :journalEntryId ORDER BY line_number ASC")
    Flux<JournalEntryLineEntity> findByJournalEntryIdOrderByLineNumberAsc(UUID journalEntryId);

    @Query("DELETE FROM accounting.journal_entry_lines WHERE journal_entry_id = :journalEntryId")
    Mono<Void> deleteByJournalEntryId(UUID journalEntryId);
}
