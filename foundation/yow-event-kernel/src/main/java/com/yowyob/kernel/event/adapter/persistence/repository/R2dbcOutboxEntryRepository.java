package com.yowyob.kernel.event.adapter.persistence.repository;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.adapter.persistence.entity.OutboxEntryEntity;
import com.yowyob.kernel.event.adapter.persistence.mapper.OutboxEntryMapper;
import com.yowyob.kernel.event.application.port.out.OutboxEntryRepository;
import com.yowyob.kernel.event.domain.enums.OutboxStatus;
import com.yowyob.kernel.event.domain.model.OutboxEntry;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.OutboxEntryId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * R2DBC implementation of {@link OutboxEntryRepository}.
 *
 * <p>The critical {@link #fetchPendingBatch(int)} method uses a raw SQL query
 * with {@code SELECT … FOR UPDATE SKIP LOCKED} to implement pessimistic row
 * locking. This pattern prevents duplicate processing when multiple poller
 * instances run concurrently in a scaled deployment.
 *
 * <p>All other operations use {@link R2dbcEntityTemplate} for clean reactive
 * CRUD without raw SQL.
 */
@Repository
public class R2dbcOutboxEntryRepository implements OutboxEntryRepository {

    /**
     * Raw SQL for locked fetch. The {@code SKIP LOCKED} hint ensures that
     * rows already locked by another connection are bypassed rather than
     * waited upon, enabling safe parallel polling.
     */
    private static final String FETCH_PENDING_SQL = """
        SELECT *
        FROM   event_bus.outbox_entries
        WHERE  status = 'PENDING'
        ORDER  BY scheduled_at ASC
        LIMIT  :batchSize
        FOR UPDATE SKIP LOCKED
        """;

    private static final String FETCH_RETRYABLE_SQL = """
        SELECT *
        FROM   event_bus.outbox_entries
        WHERE  status = 'RETRYING'
          AND  scheduled_at <= :now
        ORDER  BY scheduled_at ASC
        LIMIT  :batchSize
        FOR UPDATE SKIP LOCKED
        """;

    private final R2dbcEntityTemplate template;
    private final DatabaseClient       databaseClient;
    private final OutboxEntryMapper    mapper;

    public R2dbcOutboxEntryRepository(
            final R2dbcEntityTemplate template,
            final DatabaseClient databaseClient,
            final OutboxEntryMapper mapper) {
        this.template       = Objects.requireNonNull(template);
        this.databaseClient = Objects.requireNonNull(databaseClient);
        this.mapper         = Objects.requireNonNull(mapper);
    }

    @Override
    public Mono<OutboxEntry> save(final OutboxEntry entry) {
        OutboxEntryEntity entity = mapper.toEntity(entry);
        return template.insert(OutboxEntryEntity.class)
            .using(entity)
            .map(mapper::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses raw SQL with {@code FOR UPDATE SKIP LOCKED} for concurrent-safe
     * batch fetching. The {@link DatabaseClient} is used here instead of
     * {@link R2dbcEntityTemplate} because the template does not expose
     * locking hints.
     */
    @Override
    public Flux<OutboxEntry> fetchPendingBatch(final int batchSize) {
        return databaseClient.sql(FETCH_PENDING_SQL)
            .bind("batchSize", batchSize)
            .map((row, metadata) -> mapper.fromRow(row))
            .all();
    }

    @Override
    public Flux<OutboxEntry> fetchRetryableBatch(
            final LocalDateTime now, final int batchSize) {
        return databaseClient.sql(FETCH_RETRYABLE_SQL)
            .bind("now", now)
            .bind("batchSize", batchSize)
            .map((row, metadata) -> mapper.fromRow(row))
            .all();
    }

    @Override
    public Mono<OutboxEntry> findById(final OutboxEntryId id) {
        return template.selectOne(
            Query.query(Criteria.where("id").is(id.value())),
            OutboxEntryEntity.class
        ).map(mapper::toDomain);
    }

    @Override
    public Mono<OutboxEntry> findByEnvelopeId(final EnvelopeId envelopeId) {
        return template.selectOne(
            Query.query(Criteria.where("envelope_id").is(envelopeId.value())),
            OutboxEntryEntity.class
        ).map(mapper::toDomain);
    }

    @Override
    public Mono<Long> updateStatus(
            final OutboxEntryId id,
            final OutboxStatus status,
            final LocalDateTime processedAt) {
        Update update = Update.update("status", status.name());
        if (processedAt != null) {
            update = update.set("processed_at", processedAt);
        }
        return template.update(
            Query.query(Criteria.where("id").is(id.value())),
            update,
            OutboxEntryEntity.class
        );
    }

    @Override
    public Mono<Long> deleteProcessedOlderThan(final LocalDateTime olderThan) {
        return template.delete(
            Query.query(
                Criteria.where("status").is(OutboxStatus.PROCESSED.name())
                    .and("processed_at").lessThan(olderThan)
            ),
            OutboxEntryEntity.class
        );
    }

    @Override
    public Mono<Long> countPending() {
        return template.count(
            Query.query(Criteria.where("status").in(
                OutboxStatus.PENDING.name(), OutboxStatus.RETRYING.name()
            )),
            OutboxEntryEntity.class
        );
    }
}
