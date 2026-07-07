package com.yowyob.kernel.event.application.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.in.QueryEventStatsUseCase;
import com.yowyob.kernel.event.application.port.out.DeadLetterRepository;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventSchemaRepository;
import com.yowyob.kernel.event.application.port.out.OutboxEntryRepository;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.vo.EventBusStats;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Application service that computes event bus operational statistics.
 *
 * <p>Statistics are assembled reactively from multiple repositories to avoid
 * blocking queries. The result is a point-in-time snapshot; for real-time
 * monitoring, prefer the Micrometer gauges updated by
 * {@link yowyob.kernel.event.application.port.out.EventMetricsPort}.
 */
@Service
public class EventStatsService implements QueryEventStatsUseCase {

    private final EventEnvelopeRepository envelopeRepository;
    private final OutboxEntryRepository   outboxRepository;
    private final DeadLetterRepository    dlqRepository;
    private final EventSchemaRepository   schemaRepository;

    public EventStatsService(
            final EventEnvelopeRepository envelopeRepository,
            final OutboxEntryRepository outboxRepository,
            final DeadLetterRepository dlqRepository,
            final EventSchemaRepository schemaRepository) {
        this.envelopeRepository = Objects.requireNonNull(envelopeRepository);
        this.outboxRepository   = Objects.requireNonNull(outboxRepository);
        this.dlqRepository      = Objects.requireNonNull(dlqRepository);
        this.schemaRepository   = Objects.requireNonNull(schemaRepository);
    }

    /**
     * {@inheritDoc}
     *
     * <p>All queries run in parallel to minimise latency.
     */
    @Override
    public Mono<EventBusStats> getStats(final String tenantId) {
        Mono<Long> published  = envelopeRepository.countByStatus(EnvelopeStatus.PUBLISHED, tenantId);
        Mono<Long> failed     = envelopeRepository.countByStatus(EnvelopeStatus.FAILED, tenantId);
        Mono<Long> dead       = envelopeRepository.countByStatus(EnvelopeStatus.DEAD, tenantId);
        Mono<Long> pending    = outboxRepository.countPending();
        Mono<Long> dlqPending = dlqRepository.countWaiting(tenantId);

        return Mono.zip(published, failed, dead, pending, dlqPending)
            .map(tuple -> new EventBusStats(
                tuple.getT1(),   // totalPublished
                tuple.getT2(),   // totalFailed
                tuple.getT3(),   // totalDead
                tuple.getT4(),   // pendingInOutbox
                0.0,             // avgPublishLatencyMs — from Micrometer, not DB
                tuple.getT5(),   // dlqSize
                0,               // schemaCount — populated separately if needed
                LocalDateTime.now()
            ));
    }
}
