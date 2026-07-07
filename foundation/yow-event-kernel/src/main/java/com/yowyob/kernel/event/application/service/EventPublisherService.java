package com.yowyob.kernel.event.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxEntryRepository;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.model.OutboxEntry;

import java.util.List;
import java.util.Objects;

/**
 * Application service that implements the event publishing use cases.
 *
 * <p>Persists both the {@link DomainEventEnvelope} (event store) and the
 * corresponding {@link OutboxEntry} (delivery tracking) within the same
 * database transaction as the business operation. Actual Kafka publishing is
 * deferred to the outbox poller.
 *
 * <p>This service is <strong>not</strong> responsible for publishing to Kafka
 * directly — that is the responsibility of the outbox poller scheduled task.
 */
@Service
public class EventPublisherService implements PublishEventUseCase, PublishEventBatchUseCase {

    private final EventEnvelopeRepository envelopeRepository;
    private final OutboxEntryRepository   outboxRepository;

    public EventPublisherService(
            final EventEnvelopeRepository envelopeRepository,
            final OutboxEntryRepository outboxRepository) {
        this.envelopeRepository = Objects.requireNonNull(envelopeRepository);
        this.outboxRepository   = Objects.requireNonNull(outboxRepository);
    }

    // ── PublishEventUseCase ──────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Persists the envelope and creates its associated outbox entry in a
     * single reactive chain. Both writes participate in the caller's active
     * R2DBC transaction.
     */
    @Override
    @Transactional
    public Mono<Void> publish(final DomainEventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");

        return envelopeRepository.save(envelope)
            .flatMap(saved -> {
                OutboxEntry outboxEntry = OutboxEntry.forEnvelope(saved, null);
                return outboxRepository.save(outboxEntry);
            })
            .then();
    }

    // ── PublishEventBatchUseCase ─────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>All envelopes in the batch are saved in a single bulk-insert operation,
     * followed by a corresponding bulk-insert of their outbox entries. The entire
     * batch succeeds or fails atomically.
     */
    @Override
    @Transactional
    public Mono<Integer> publishAll(final List<DomainEventEnvelope> envelopes) {
        Objects.requireNonNull(envelopes, "envelopes must not be null");
        if (envelopes.isEmpty()) {
            throw new IllegalArgumentException("Cannot publish an empty batch");
        }

        return envelopeRepository.saveAll(envelopes)
            .flatMap(savedCount -> {
                List<OutboxEntry> outboxEntries = envelopes.stream()
                    .map(e -> OutboxEntry.forEnvelope(e, null))
                    .toList();
                // Bulk-save all outbox entries; the count returned is for outbox rows
                return Mono.just(outboxEntries)
                    .flatMapMany(list -> {
                        // Save each entry and collect; R2DBC batch insert is handled
                        // by the repository implementation via COPY or multi-row INSERT
                        return reactor.core.publisher.Flux.fromIterable(list)
                            .flatMap(outboxRepository::save);
                    })
                    .count()
                    .thenReturn(savedCount);
            });
    }
}
