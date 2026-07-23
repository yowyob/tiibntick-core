package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.accounting.application.port.out.AccountingEventPublisher;
import com.yowyob.tiibntick.core.accounting.domain.event.AccountingPeriodClosedEvent;
import com.yowyob.tiibntick.core.accounting.domain.event.JournalEntryPostedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link AccountingEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly. Envelopes are persisted in the same DB transaction as
 * the business write (see the {@code @Transactional} boundaries on
 * {@code AccountingApplicationService#postJournalEntry}/{@code #closePeriod}), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ — a
 * business save can no longer succeed while its event is silently lost.
 *
 * <p>The Kafka wire format is unchanged: the payload is still simply
 * {@code objectMapper.writeValueAsString(event)} — the raw event record serialized
 * directly, with no extra wrapper envelope — so existing consumers require no change.
 *
 * @author MANFOUO Braun
 */
@Component
public class AccountingEventPublisherAdapter implements AccountingEventPublisher {

    static final String TOPIC_JOURNAL_POSTED = "tnt.accounting.journal-entry.posted";
    static final String TOPIC_PERIOD_CLOSED  = "tnt.accounting.period.closed";

    private static final String AGGREGATE_TYPE_JOURNAL_ENTRY = "JournalEntry";
    private static final String AGGREGATE_TYPE_ACCOUNTING_PERIOD = "AccountingPeriod";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public AccountingEventPublisherAdapter(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishJournalEntryPosted(JournalEntryPostedEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(event.journalEntryId().toString())
                        .aggregateType(AGGREGATE_TYPE_JOURNAL_ENTRY)
                        .tenantId(event.tenantId().toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(TOPIC_JOURNAL_POSTED)
                        .occurredAt(LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish);
    }

    @Override
    public Mono<Void> publishPeriodClosed(AccountingPeriodClosedEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(event.periodId().toString())
                        .aggregateType(AGGREGATE_TYPE_ACCOUNTING_PERIOD)
                        .tenantId(event.tenantId().toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(TOPIC_PERIOD_CLOSED)
                        .occurredAt(LocalDateTime.ofInstant(event.closedAt(), ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish);
    }
}
