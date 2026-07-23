package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference;
import reactor.core.publisher.Mono;

/**
 * Outbound port generating a new, globally unique {@link DisputeReference}.
 *
 * <p>Implementations must guarantee uniqueness across every application instance in a
 * multi-instance deployment (Chantier D · Audit n°6 · S1) — the production adapter draws
 * from the {@code dispute_reference_seq} PostgreSQL sequence, which Postgres itself
 * serializes across concurrent connections, rather than an in-process counter.
 *
 * @author MANFOUO Braun
 */
public interface IDisputeReferenceGenerator {

    /**
     * Atomically obtains the next reference in the sequence.
     *
     * @return a Mono emitting a fresh, never-before-issued {@link DisputeReference}
     */
    Mono<DisputeReference> nextReference();
}
