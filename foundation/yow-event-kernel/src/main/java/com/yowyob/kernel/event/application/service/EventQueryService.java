package com.yowyob.kernel.event.application.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.in.QueryEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Application service for querying the event store.
 *
 * <p>All reads are performed against the {@link EventEnvelopeRepository} which
 * targets the {@code yow_kernel_db} event store. Results are filtered by
 * {@code tenantId} to enforce multi-tenant isolation at the application layer
 * (in addition to the PostgreSQL RLS policy).
 */
@Service
public class EventQueryService implements QueryEventUseCase {

    private final EventEnvelopeRepository repository;

    public EventQueryService(final EventEnvelopeRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Mono<DomainEventEnvelope> findById(final EnvelopeId envelopeId, final String tenantId) {
        Objects.requireNonNull(envelopeId, "envelopeId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findById(envelopeId, tenantId);
    }

    @Override
    public Flux<DomainEventEnvelope> findByAggregateId(
            final String aggregateId,
            final String aggregateType,
            final String tenantId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findByAggregateId(aggregateId, aggregateType, tenantId);
    }

    @Override
    public Flux<DomainEventEnvelope> findByEventType(
            final String eventType,
            final LocalDateTime from,
            final LocalDateTime to,
            final String tenantId,
            final int limit) {
        if (limit < 1 || limit > 1000) {
            return Flux.error(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        return repository.findByEventType(eventType, from, to, tenantId, limit);
    }

    @Override
    public Flux<DomainEventEnvelope> findByCorrelationId(
            final String correlationId, final String tenantId) {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        return repository.findByCorrelationId(correlationId, tenantId);
    }

    @Override
    public Mono<Boolean> verifyPayloadIntegrity(
            final EnvelopeId envelopeId, final String payloadHash) {
        return repository.findById(envelopeId, null) // cross-tenant integrity check
            .map(envelope -> envelope.getPayloadHash().equals(payloadHash))
            .defaultIfEmpty(false);
    }
}
