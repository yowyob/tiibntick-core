package com.yowyob.tiibntick.core.sync.adapter.out.persistence;

import com.yowyob.tiibntick.core.sync.adapter.out.persistence.entity.EntityVersionEntity;
import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

@Repository
public class R2dbcEntityVersionRepository implements IEntityVersionRepository {

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;

    public R2dbcEntityVersionRepository(R2dbcEntityTemplate template, DatabaseClient databaseClient) {
        this.template = template;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> upsert(EntityVersionRecord record) {
        String sql = """
                INSERT INTO tnt_entity_version
                    (tenant_id, aggregate_type, aggregate_id, version, operation, payload_json, updated_at, updated_by_user_id)
                VALUES (:tenantId, :aggregateType, :aggregateId, :version, :operation, :payload, :updatedAt, :updatedBy)
                ON CONFLICT (tenant_id, aggregate_type, aggregate_id)
                DO UPDATE SET
                    version = EXCLUDED.version,
                    operation = EXCLUDED.operation,
                    payload_json = EXCLUDED.payload_json,
                    updated_at = EXCLUDED.updated_at,
                    updated_by_user_id = EXCLUDED.updated_by_user_id
                """;

        return databaseClient.sql(sql)
                .bind("tenantId", record.tenantId())
                .bind("aggregateType", record.aggregateType())
                .bind("aggregateId", record.aggregateId())
                .bind("version", record.version())
                .bind("operation", record.operation().name())
                .bind("payload", record.payloadJson() != null ? record.payloadJson() : "")
                .bind("updatedAt", record.updatedAt())
                .bind("updatedBy", record.updatedByUserId() != null ? record.updatedByUserId() : "system")
                .then();
    }

    @Override
    public Mono<EntityVersionRecord> findCurrent(String tenantId, String aggregateType, String aggregateId) {
        return template.selectOne(
                Query.query(Criteria.where("tenant_id").is(tenantId)
                        .and("aggregate_type").is(aggregateType)
                        .and("aggregate_id").is(aggregateId)),
                EntityVersionEntity.class)
                .map(EntityVersionEntity::toDomain);
    }

    @Override
    public Flux<EntityVersionRecord> findChangedSince(String tenantId, LocalDateTime since,
                                                       Set<String> filterAggregates, int limit) {
        Criteria base = Criteria.where("tenant_id").is(tenantId)
                .and("updated_at").greaterThan(since);

        if (filterAggregates != null && !filterAggregates.isEmpty()) {
            base = base.and("aggregate_type").in(filterAggregates);
        }

        return template.select(Query.query(base).limit(limit), EntityVersionEntity.class)
                .map(EntityVersionEntity::toDomain);
    }

    @Override
    public Mono<Long> countChangedSince(String tenantId, LocalDateTime since) {
        return template.count(
                Query.query(Criteria.where("tenant_id").is(tenantId)
                        .and("updated_at").greaterThan(since)),
                EntityVersionEntity.class);
    }

    @Override
    public Flux<EntityVersionRecord> findAllCurrentByType(String tenantId, String aggregateType) {
        return template.select(
                Query.query(Criteria.where("tenant_id").is(tenantId)
                        .and("aggregate_type").is(aggregateType)),
                EntityVersionEntity.class)
                .map(EntityVersionEntity::toDomain);
    }
}
