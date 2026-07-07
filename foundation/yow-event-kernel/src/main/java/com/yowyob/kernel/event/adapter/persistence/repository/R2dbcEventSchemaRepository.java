package com.yowyob.kernel.event.adapter.persistence.repository;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.adapter.persistence.entity.EventSchemaEntity;
import com.yowyob.kernel.event.adapter.persistence.mapper.EventSchemaMapper;
import com.yowyob.kernel.event.application.port.out.EventSchemaRepository;
import com.yowyob.kernel.event.domain.model.EventSchema;
import com.yowyob.kernel.event.domain.vo.SchemaId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * R2DBC implementation of {@link EventSchemaRepository}.
 *
 * <p>Targets the {@code event_bus.event_schemas} table in {@code yow_kernel_db}.
 * Schemas are immutable once registered; only {@code deprecated_at} can be updated.
 */
@Repository
public class R2dbcEventSchemaRepository implements EventSchemaRepository {

    private final R2dbcEntityTemplate template;
    private final EventSchemaMapper   mapper;

    public R2dbcEventSchemaRepository(
            final R2dbcEntityTemplate template,
            final EventSchemaMapper mapper) {
        this.template = Objects.requireNonNull(template);
        this.mapper   = Objects.requireNonNull(mapper);
    }

    @Override
    public Mono<EventSchema> save(final EventSchema schema) {
        return template.insert(EventSchemaEntity.class)
            .using(mapper.toEntity(schema))
            .map(mapper::toDomain);
    }

    @Override
    public Mono<EventSchema> findById(final SchemaId id) {
        return template.selectOne(
            Query.query(Criteria.where("id").is(id.value())),
            EventSchemaEntity.class
        ).map(mapper::toDomain);
    }

    @Override
    public Mono<EventSchema> findLatest(final String eventType, final String solutionCode) {
        // Select the schema with the highest version number for this event type + solution
        return template.select(
            Query.query(
                Criteria.where("event_type").is(eventType)
                    .and("solution_code").is(solutionCode)
            ).sort(org.springframework.data.domain.Sort.by("version").descending())
             .limit(1),
            EventSchemaEntity.class
        ).next().map(mapper::toDomain);
    }

    @Override
    public Mono<EventSchema> findByVersion(
            final String eventType, final String solutionCode, final int version) {
        return template.selectOne(
            Query.query(
                Criteria.where("event_type").is(eventType)
                    .and("solution_code").is(solutionCode)
                    .and("version").is(version)
            ),
            EventSchemaEntity.class
        ).map(mapper::toDomain);
    }

    @Override
    public Flux<EventSchema> findAllVersions(
            final String eventType, final String solutionCode) {
        return template.select(
            Query.query(
                Criteria.where("event_type").is(eventType)
                    .and("solution_code").is(solutionCode)
            ).sort(org.springframework.data.domain.Sort.by("version").ascending()),
            EventSchemaEntity.class
        ).map(mapper::toDomain);
    }

    @Override
    public Mono<Long> markDeprecated(final SchemaId id) {
        return template.update(
            Query.query(Criteria.where("id").is(id.value())),
            Update.update("deprecated_at", LocalDateTime.now()),
            EventSchemaEntity.class
        );
    }

    @Override
    public Mono<Boolean> existsForEventType(
            final String eventType, final String solutionCode) {
        return template.count(
            Query.query(
                Criteria.where("event_type").is(eventType)
                    .and("solution_code").is(solutionCode)
            ),
            EventSchemaEntity.class
        ).map(count -> count > 0);
    }
}
