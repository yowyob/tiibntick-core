package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.accounting.application.port.out.AccountingEventPublisher;
import com.yowyob.tiibntick.core.accounting.domain.event.AccountingPeriodClosedEvent;
import com.yowyob.tiibntick.core.accounting.domain.event.JournalEntryPostedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka adapter implementing AccountingEventPublisher port.
 * Publishes accounting domain events to dedicated Kafka topics.
 * Author: MANFOUO Braun
 */
@Component
public class AccountingEventPublisherAdapter implements AccountingEventPublisher {

    static final String TOPIC_JOURNAL_POSTED = "tnt.accounting.journal-entry.posted";
    static final String TOPIC_PERIOD_CLOSED  = "tnt.accounting.period.closed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AccountingEventPublisherAdapter(
            @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    @Override
    public Mono<Void> publishJournalEntryPosted(JournalEntryPostedEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(payload -> Mono.fromFuture(
                        kafkaTemplate.send(TOPIC_JOURNAL_POSTED,
                                event.journalEntryId().toString(), payload).toCompletableFuture()))
                .then()
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    // Non-blocking: log and continue — event can be replayed from audit
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> publishPeriodClosed(AccountingPeriodClosedEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(payload -> Mono.fromFuture(
                        kafkaTemplate.send(TOPIC_PERIOD_CLOSED,
                                event.periodId().toString(), payload).toCompletableFuture()))
                .then()
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.empty());
    }
}
