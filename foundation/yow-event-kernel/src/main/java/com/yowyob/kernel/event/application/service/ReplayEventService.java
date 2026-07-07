package com.yowyob.kernel.event.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.in.ReplayEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventIdempotencyStorePort;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Application service that replays previously published events from the event store.
 *
 * <p>Replayed events are re-published to their original Kafka topic via
 * {@link KafkaPublisherPort#publishAsReplay} with the {@code X-Yow-Replay: true}
 * header so that idempotent consumers can distinguish replays from live events.
 *
 * <p>Before replaying, the service clears the idempotency record for each
 * envelope's correlation ID so that downstream consumers will accept the
 * replayed event even if it was previously processed.
 * 
 * @author MANFOUO Braun
 */
public class ReplayEventService implements ReplayEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReplayEventService.class);

    /** Default upper bound for a single replay query to prevent OOM. */
    private static final int DEFAULT_REPLAY_LIMIT = 10_000;

    /** TTL used when re-marking a replayed correlation ID as processed. */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final EventEnvelopeRepository   envelopeRepository;
    private final KafkaPublisherPort        kafkaPublisher;
    private final EventIdempotencyStorePort idempotencyStore;

    public ReplayEventService(
            final EventEnvelopeRepository envelopeRepository,
            final KafkaPublisherPort kafkaPublisher,
            final EventIdempotencyStorePort idempotencyStore) {
        this.envelopeRepository = Objects.requireNonNull(envelopeRepository);
        this.kafkaPublisher     = Objects.requireNonNull(kafkaPublisher);
        this.idempotencyStore   = Objects.requireNonNull(idempotencyStore);
    }

    @Override
    public Flux<DomainEventEnvelope> replayByAggregate(
            final String aggregateId,
            final String aggregateType,
            final String tenantId) {

        log.info("Replaying events for aggregate {} (type={}, tenant={})",
                aggregateId, aggregateType, tenantId);

        return envelopeRepository.findByAggregateId(aggregateId, aggregateType, tenantId)
            .flatMap(this::replaySingle);
    }

    @Override
    public Flux<DomainEventEnvelope> replayByEventType(
            final String eventType,
            final LocalDateTime from,
            final LocalDateTime to,
            final String tenantId) {

        log.info("Replaying events of type {} in window [{}, {}] (tenant={})",
                eventType, from, to, tenantId);

        return envelopeRepository.findByEventType(eventType, from, to, tenantId, DEFAULT_REPLAY_LIMIT)
            .flatMap(this::replaySingle);
    }

    @Override
    public Mono<Long> countReplayable(
            final String eventType,
            final LocalDateTime from,
            final LocalDateTime to,
            final String tenantId) {

        return envelopeRepository.findByEventType(eventType, from, to, tenantId, DEFAULT_REPLAY_LIMIT)
            .count();
    }

    // ── Internal ────────────────────────────────────────────────────────────

    /**
     * Clears the idempotency record for the envelope's correlation ID and
     * re-publishes it as a replay event.
     */
    private Mono<DomainEventEnvelope> replaySingle(final DomainEventEnvelope envelope) {
        return idempotencyStore.clear(envelope.getCorrelationId())
            .then(kafkaPublisher.publishAsReplay(envelope))
            .then(idempotencyStore.markAsProcessed(envelope.getCorrelationId(), IDEMPOTENCY_TTL))
            .thenReturn(envelope)
            .doOnNext(env -> log.debug("Replayed envelope {} (correlation={})",
                    env.getId().value(), env.getCorrelationId()));
    }
}
