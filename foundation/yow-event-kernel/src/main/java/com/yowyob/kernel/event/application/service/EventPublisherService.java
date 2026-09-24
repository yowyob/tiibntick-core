package com.yowyob.kernel.event.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

import java.util.List;
import java.util.Objects;

/**
 * Application service that implements the event publishing use cases.
 *
 * <p>Persists the {@link DomainEventEnvelope} (event store, kept for audit/
 * replay via {@code EventQueryService}/{@code ReplayEventService}) then
 * publishes to Kafka directly in the same call — no outbox relay, no retry.
 *
 * <p><b>2026-09-18 decision:</b> the transactional-outbox-plus-poller
 * mechanism ({@code OutboxPollerService}) was decommissioned by explicit
 * request, trading its delivery guarantee for lower idle CPU/DB load. If
 * Kafka is unavailable when {@link #publish} is called, the publish fails,
 * the envelope is marked {@code FAILED} (visible via the query/stats use
 * cases, never retried automatically), and the event is lost — this is the
 * accepted risk, not a bug. Also note this only protects against Kafka being
 * down: {@code envelope.save()} and the Kafka publish happen inside this
 * method's own transaction, so if the *caller's* enclosing business
 * transaction later rolls back for an unrelated reason, the Kafka message
 * may already have gone out for a write that never actually committed — the
 * old outbox pattern prevented that too; this trade-off is inherent to
 * publishing directly instead of relaying through a table.
 */
@Service
public class EventPublisherService implements PublishEventUseCase, PublishEventBatchUseCase {

    private final EventEnvelopeRepository envelopeRepository;
    private final KafkaPublisherPort      kafkaPublisher;
    private final EventMetricsPort        metrics;

    public EventPublisherService(
            final EventEnvelopeRepository envelopeRepository,
            final KafkaPublisherPort kafkaPublisher,
            final EventMetricsPort metrics) {
        this.envelopeRepository = Objects.requireNonNull(envelopeRepository);
        this.kafkaPublisher     = Objects.requireNonNull(kafkaPublisher);
        this.metrics            = Objects.requireNonNull(metrics);
    }

    // ── PublishEventUseCase ──────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Persists the envelope, then publishes it to Kafka directly — no
     * outbox entry is created.
     */
    @Override
    @Transactional
    public Mono<Void> publish(final DomainEventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");

        return envelopeRepository.save(envelope)
            .flatMap(this::publishDirectly);
    }

    // ── PublishEventBatchUseCase ─────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>All envelopes are bulk-saved, then each is published to Kafka
     * directly. A failure publishing one envelope does not stop the rest of
     * the batch — each is isolated via {@code onErrorResume} inside
     * {@link #publishDirectly}.
     */
    @Override
    @Transactional
    public Mono<Integer> publishAll(final List<DomainEventEnvelope> envelopes) {
        Objects.requireNonNull(envelopes, "envelopes must not be null");
        if (envelopes.isEmpty()) {
            throw new IllegalArgumentException("Cannot publish an empty batch");
        }

        return envelopeRepository.saveAll(envelopes)
            .flatMap(savedCount -> Flux.fromIterable(envelopes)
                .flatMap(this::publishDirectly)
                .then(Mono.just(savedCount)));
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Publishes one envelope to Kafka and persists the resulting status —
     * PUBLISHED on success, FAILED on error (see class Javadoc for what
     * happens to a failed envelope now that nothing retries it).
     */
    private Mono<Void> publishDirectly(final DomainEventEnvelope envelope) {
        long startMs = System.currentTimeMillis();

        return kafkaPublisher.publish(envelope)
            .then(Mono.defer(() -> {
                long elapsed = System.currentTimeMillis() - startMs;
                envelope.markPublished();
                metrics.recordPublished(envelope, elapsed);
                return envelopeRepository.updateStatus(
                    envelope.getId(), envelope.getStatus(), envelope.getPublishedAt(),
                    null, envelope.getRetryCount(), envelope.getVersion());
            }))
            .onErrorResume(error -> {
                metrics.recordFailed(envelope, error.getClass().getSimpleName());
                envelope.markFailed(error.getMessage());
                return envelopeRepository.updateStatus(
                    envelope.getId(), envelope.getStatus(), null,
                    error.getMessage(), envelope.getRetryCount(), envelope.getVersion());
            })
            .then();
    }
}
