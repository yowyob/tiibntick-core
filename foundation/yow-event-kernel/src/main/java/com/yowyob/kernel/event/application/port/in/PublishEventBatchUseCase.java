package com.yowyob.kernel.event.application.port.in;

import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

import java.util.List;

/**
 * <b>Inbound port</b> — Atomically enqueue multiple domain event envelopes in
 * a single outbox write.
 *
 * <p>All envelopes in the batch must share the same database transaction.
 * If any envelope fails validation, the entire batch is rejected.
 *
 * <p>Use this port when a single business operation produces several events
 * (e.g. a mission creation that also raises a package-registered event).
 */
public interface PublishEventBatchUseCase {

    /**
     * Persists a collection of envelopes atomically in the transactional outbox.
     *
     * @param envelopes the envelopes to enqueue — must not be {@code null} or empty
     * @return a {@link Mono} emitting the number of envelopes actually persisted,
     *         or an error signal if the batch write fails
     * @throws IllegalArgumentException if the list is null or empty
     */
    Mono<Integer> publishAll(List<DomainEventEnvelope> envelopes);
}
