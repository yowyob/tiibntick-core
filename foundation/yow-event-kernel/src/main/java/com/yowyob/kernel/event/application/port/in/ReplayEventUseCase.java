package com.yowyob.kernel.event.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

import java.time.LocalDateTime;

/**
 * <b>Inbound port</b> — Replay previously published events from the event store.
 *
 * <p>Event replay is a critical infrastructure capability used in several
 * operational scenarios:
 * <ul>
 *   <li>Rebuilding a read model (projection) from scratch after a bug fix.</li>
 *   <li>Populating a new downstream consumer that missed historical events.</li>
 *   <li>Debugging and root-cause analysis of past system behaviour.</li>
 * </ul>
 *
 * <p><strong>Important:</strong> Replayed events are re-published to their original
 * Kafka topic with a special header {@code X-Yow-Replay: true} so that
 * idempotent consumers can distinguish replay messages from live events.
 *
 * <p>Replay operations are rate-limited and require an admin role.
 * 
 * @author MANFOUO Braun
 */
public interface ReplayEventUseCase {

    /**
     * Replays all events emitted by a specific aggregate instance.
     *
     * @param aggregateId   the aggregate's unique identifier
     * @param aggregateType the aggregate type discriminator
     * @param tenantId      the owning tenant
     * @return a {@link Flux} emitting each replayed envelope as it is published
     */
    Flux<DomainEventEnvelope> replayByAggregate(
            String aggregateId,
            String aggregateType,
            String tenantId);

    /**
     * Replays all events of a given type within a time window.
     *
     * @param eventType the fully qualified event type name
     * @param from      start of the replay window (inclusive)
     * @param to        end of the replay window (inclusive)
     * @param tenantId  the owning tenant
     * @return a {@link Flux} of re-published envelopes
     */
    Flux<DomainEventEnvelope> replayByEventType(
            String eventType,
            LocalDateTime from,
            LocalDateTime to,
            String tenantId);

    /**
     * Returns the count of events that would be replayed for the given criteria,
     * without actually publishing them. Useful for capacity planning.
     *
     * @param eventType the event type to count
     * @param from      start of the window
     * @param to        end of the window
     * @param tenantId  the owning tenant
     * @return a {@link Mono} emitting the event count
     */
    Mono<Long> countReplayable(
            String eventType,
            LocalDateTime from,
            LocalDateTime to,
            String tenantId);
}
