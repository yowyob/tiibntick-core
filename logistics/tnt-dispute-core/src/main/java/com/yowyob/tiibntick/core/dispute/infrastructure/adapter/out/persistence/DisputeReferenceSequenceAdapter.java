package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeReferenceGenerator;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * R2DBC adapter for {@link IDisputeReferenceGenerator} backed by the
 * {@code dispute_reference_seq} PostgreSQL sequence (see
 * {@code db/changelog/changes/009_create_dispute_reference_sequence.sql}).
 *
 * <p>Replaces the previous static {@code AtomicInteger} in {@code DisputeReference}
 * (Chantier D · Audit n°6 · S1): {@code nextval()} is atomic and visible across every
 * connection — including every application instance — sharing the sequence, so
 * concurrent {@code openDispute()} calls across a multi-instance deployment can never be
 * handed the same number, unlike a per-JVM in-memory counter.
 *
 * @author MANFOUO Braun
 */
@Component
public class DisputeReferenceSequenceAdapter implements IDisputeReferenceGenerator {

    private final DatabaseClient databaseClient;

    public DisputeReferenceSequenceAdapter(final DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<DisputeReference> nextReference() {
        return databaseClient.sql("SELECT nextval('dispute_reference_seq') AS seq")
                .map(row -> row.get("seq", Long.class))
                .one()
                .map(DisputeReference::forSequence);
    }
}
