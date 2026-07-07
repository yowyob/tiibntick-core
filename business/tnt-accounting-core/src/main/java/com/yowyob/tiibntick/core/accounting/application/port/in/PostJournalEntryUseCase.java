package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.JournalEntry;
import reactor.core.publisher.Mono;

/**
 * Use case: Validate, post and persist a balanced journal entry to the general ledger.
 * This also updates the running balance of each involved account.
 * Author: MANFOUO Braun
 */
public interface PostJournalEntryUseCase {
    Mono<JournalEntry> postJournalEntry(PostJournalEntryCommand command);
}
