package com.yowyob.kernel.event.adapter.persistence.repository;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.adapter.persistence.entity.DeadLetterEntryEntity;
import com.yowyob.kernel.event.adapter.persistence.mapper.DeadLetterEntryMapper;
import com.yowyob.kernel.event.application.port.out.DeadLetterRepository;
import com.yowyob.kernel.event.domain.enums.DLQStatus;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.vo.DeadLetterEntryId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * R2DBC implementation of {@link DeadLetterRepository}.
 *
 * <p>All operations target the {@code event_bus.dead_letter_entries} table
 * in {@code yow_kernel_db}. Row-Level Security is activated on this table
 * via the connection factory tenant interceptor.
 */
@Repository
public class R2dbcDeadLetterRepository implements DeadLetterRepository {

    private final R2dbcEntityTemplate    template;
    private final DeadLetterEntryMapper  mapper;

    public R2dbcDeadLetterRepository(
            final R2dbcEntityTemplate template,
            final DeadLetterEntryMapper mapper) {
        this.template = Objects.requireNonNull(template);
        this.mapper   = Objects.requireNonNull(mapper);
    }

    @Override
    public Mono<DeadLetterEntry> save(final DeadLetterEntry entry) {
        return template.insert(DeadLetterEntryEntity.class)
            .using(mapper.toEntity(entry))
            .map(mapper::toDomain);
    }

    @Override
    public Mono<DeadLetterEntry> findById(
            final DeadLetterEntryId id, final String tenantId) {
        Criteria criteria = Criteria.where("id").is(id.value());
        // Note: dead_letter_entries does not have tenant_id directly;
        // RLS on the original envelope provides isolation.
        // If explicit tenant filtering is needed, add tenant_id column to DDL.
        return template.selectOne(
            Query.query(criteria),
            DeadLetterEntryEntity.class
        ).map(mapper::toDomain);
    }

    @Override
    public Flux<DeadLetterEntry> findWaiting(final String tenantId) {
        // tenantId = null means all tenants (admin role)
        Query query = Query.query(Criteria.where("status").is(DLQStatus.WAITING.name()))
            .sort(org.springframework.data.domain.Sort.by("failed_at").ascending());
        return template.select(query, DeadLetterEntryEntity.class)
            .map(mapper::toDomain);
    }

    @Override
    public Flux<DeadLetterEntry> findWaitingByTopic(
            final String kafkaTopic, final String tenantId) {
        Query query = Query.query(
            Criteria.where("status").is(DLQStatus.WAITING.name())
                .and("kafka_topic").is(kafkaTopic)
        ).sort(org.springframework.data.domain.Sort.by("failed_at").ascending());
        return template.select(query, DeadLetterEntryEntity.class)
            .map(mapper::toDomain);
    }

    @Override
    public Mono<Long> updateStatus(
            final DeadLetterEntryId id,
            final DLQStatus status,
            final LocalDateTime reprocessedAt,
            final String discardReason) {
        Update update = Update.update("status", status.name());
        if (reprocessedAt != null) {
            update = update.set("reprocessed_at", reprocessedAt);
        }
        if (discardReason != null) {
            update = update.set("discard_reason", discardReason);
        }
        return template.update(
            Query.query(Criteria.where("id").is(id.value())),
            update,
            DeadLetterEntryEntity.class
        );
    }

    @Override
    public Mono<Long> countWaiting(final String tenantId) {
        return template.count(
            Query.query(Criteria.where("status").is(DLQStatus.WAITING.name())),
            DeadLetterEntryEntity.class
        );
    }
}
