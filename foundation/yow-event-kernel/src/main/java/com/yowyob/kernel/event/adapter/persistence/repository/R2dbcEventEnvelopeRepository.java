package com.yowyob.kernel.event.adapter.persistence.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.adapter.persistence.entity.DomainEventEnvelopeEntity;
import com.yowyob.kernel.event.adapter.persistence.mapper.EventEnvelopeMapper;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * R2DBC implementation of {@link EventEnvelopeRepository}.
 *
 * <p>Targets the {@code event_bus.domain_event_envelopes} table in
 * {@code yow_kernel_db} — see
 * {@code db/changelog/changes/002-create-domain-event-envelopes-table.sql}.
 *
 * <p>{@code tenantId} filters are applied only when non-{@code null}: some
 * callers (integrity checks, admin/cross-tenant reads — see
 * {@link com.yowyob.kernel.event.application.service.EventQueryService#verifyPayloadIntegrity}
 * and {@link com.yowyob.kernel.event.application.service.OutboxPollerService#processEntry})
 * intentionally pass {@code null} to bypass tenant scoping.
 *
 * <p>{@link #updateStatus} implements manual optimistic locking: the row is
 * only updated when its persisted {@code version} column equals
 * {@code version - 1} (the pre-transition value) — the domain aggregate
 * increments {@code version} itself on every state transition (see
 * {@link DomainEventEnvelope#markPublished()} and sibling methods) before
 * calling this method with the already-incremented value. A returned count of
 * {@code 0} signals a concurrent modification.
 */
@Repository
public class R2dbcEventEnvelopeRepository implements EventEnvelopeRepository {

    private final R2dbcEntityTemplate template;
    private final EventEnvelopeMapper mapper;

    public R2dbcEventEnvelopeRepository(
            final R2dbcEntityTemplate template,
            final EventEnvelopeMapper mapper) {
        this.template = Objects.requireNonNull(template);
        this.mapper   = Objects.requireNonNull(mapper);
    }

    @Override
    public Mono<DomainEventEnvelope> save(final DomainEventEnvelope envelope) {
        return template.insert(DomainEventEnvelopeEntity.class)
            .using(mapper.toEntity(envelope))
            .map(mapper::toDomain);
    }

    @Override
    public Mono<Integer> saveAll(final Iterable<DomainEventEnvelope> envelopes) {
        return Flux.fromIterable(envelopes)
            .flatMap(this::save)
            .count()
            .map(Long::intValue);
    }

    @Override
    public Mono<DomainEventEnvelope> findById(final EnvelopeId id, final String tenantId) {
        Criteria criteria = Criteria.where("id").is(id.value());
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        return template.selectOne(Query.query(criteria), DomainEventEnvelopeEntity.class)
            .map(mapper::toDomain);
    }

    @Override
    public Flux<DomainEventEnvelope> findByAggregateId(
            final String aggregateId, final String aggregateType, final String tenantId) {
        Criteria criteria = Criteria.where("aggregate_id").is(aggregateId)
            .and("aggregate_type").is(aggregateType);
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        Query query = Query.query(criteria).sort(Sort.by("occurred_at").ascending());
        return template.select(query, DomainEventEnvelopeEntity.class).map(mapper::toDomain);
    }

    @Override
    public Flux<DomainEventEnvelope> findByEventType(
            final String eventType,
            final LocalDateTime from,
            final LocalDateTime to,
            final String tenantId,
            final int limit) {
        Criteria criteria = Criteria.where("event_type").is(eventType)
            .and("occurred_at").between(from, to);
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        Query query = Query.query(criteria)
            .sort(Sort.by("occurred_at").ascending())
            .limit(limit);
        return template.select(query, DomainEventEnvelopeEntity.class).map(mapper::toDomain);
    }

    @Override
    public Flux<DomainEventEnvelope> findByCorrelationId(
            final String correlationId, final String tenantId) {
        Criteria criteria = Criteria.where("correlation_id").is(correlationId);
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        Query query = Query.query(criteria).sort(Sort.by("occurred_at").ascending());
        return template.select(query, DomainEventEnvelopeEntity.class).map(mapper::toDomain);
    }

    @Override
    public Mono<Integer> updateStatus(
            final EnvelopeId id,
            final EnvelopeStatus status,
            final LocalDateTime publishedAt,
            final String lastError,
            final int retryCount,
            final int version) {
        Update update = Update.update("status", status.name())
            .set("retry_count", retryCount)
            .set("last_error", lastError)
            .set("published_at", publishedAt)
            .set("version", version);

        Criteria criteria = Criteria.where("id").is(id.value())
            .and("version").is(version - 1);

        return template.update(Query.query(criteria), update, DomainEventEnvelopeEntity.class)
            .map(Long::intValue);
    }

    // ── Outbox relay ─────────────────────────────────────────────────────────
    // Raw SQL: next_attempt_at is a relay-only column (changeset 006) deliberately absent from
    // DomainEventEnvelopeEntity, so that inserts never send it and the DEFAULT NOW() applies;
    // every time comparison is done with the database clock.

    private static final String RELAY_CANDIDATES_SQL = """
        SELECT * FROM event_bus.domain_event_envelopes
         WHERE (status IN ('FAILED', 'RETRYING') AND next_attempt_at <= NOW())
            OR (status = 'PENDING' AND next_attempt_at <= NOW() - make_interval(secs => :graceSeconds))
         ORDER BY next_attempt_at
         LIMIT :limit
        """;

    private static final String CLAIM_SQL = """
        UPDATE event_bus.domain_event_envelopes
           SET status = 'RETRYING',
               version = version + 1,
               next_attempt_at = NOW() + make_interval(secs => :claimSeconds)
         WHERE id = :id AND version = :version AND status IN ('PENDING', 'FAILED', 'RETRYING')
        """;

    private static final String RELAY_FAILURE_SQL = """
        UPDATE event_bus.domain_event_envelopes
           SET status = :status,
               last_error = :lastError,
               retry_count = :retryCount,
               version = :version,
               next_attempt_at = NOW() + make_interval(secs => :retrySeconds)
         WHERE id = :id AND version = :previousVersion
        """;

    @Override
    public Flux<DomainEventEnvelope> findRelayCandidates(final Duration pendingGrace, final int limit) {
        return template.getDatabaseClient().sql(RELAY_CANDIDATES_SQL)
            .bind("graceSeconds", seconds(pendingGrace))
            .bind("limit", limit)
            .map((row, meta) -> template.getConverter().read(DomainEventEnvelopeEntity.class, row, meta))
            .all()
            .map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> claimForRelay(final EnvelopeId id, final int expectedVersion, final Duration claimTimeout) {
        return template.getDatabaseClient().sql(CLAIM_SQL)
            .bind("claimSeconds", seconds(claimTimeout))
            .bind("id", id.value())
            .bind("version", expectedVersion)
            .fetch().rowsUpdated()
            .map(count -> count == 1L);
    }

    @Override
    public Mono<Integer> recordRelayFailure(
            final EnvelopeId id,
            final EnvelopeStatus status,
            final String lastError,
            final int retryCount,
            final int version,
            final Duration retryIn) {
        var spec = template.getDatabaseClient().sql(RELAY_FAILURE_SQL)
            .bind("status", status.name())
            .bind("retryCount", retryCount)
            .bind("version", version)
            .bind("retrySeconds", seconds(retryIn))
            .bind("id", id.value())
            .bind("previousVersion", version - 1);
        spec = lastError == null ? spec.bindNull("lastError", String.class) : spec.bind("lastError", lastError);
        return spec.fetch().rowsUpdated().map(Long::intValue);
    }

    private static double seconds(final Duration duration) {
        return duration == null ? 0d : duration.toMillis() / 1000d;
    }

    @Override
    public Mono<Long> countByStatus(final EnvelopeStatus status, final String tenantId) {
        Criteria criteria = Criteria.where("status").is(status.name());
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        return template.count(Query.query(criteria), DomainEventEnvelopeEntity.class);
    }
}
