package com.yowyob.tiibntick.core.accounting.application.port.out;

import com.yowyob.tiibntick.core.accounting.domain.event.AccountingPeriodClosedEvent;
import com.yowyob.tiibntick.core.accounting.domain.event.JournalEntryPostedEvent;
import reactor.core.publisher.Mono;

/** Publishes accounting domain events to Kafka. Author: MANFOUO Braun */
public interface AccountingEventPublisher {
    Mono<Void> publishJournalEntryPosted(JournalEntryPostedEvent event);
    Mono<Void> publishPeriodClosed(AccountingPeriodClosedEvent event);
}
