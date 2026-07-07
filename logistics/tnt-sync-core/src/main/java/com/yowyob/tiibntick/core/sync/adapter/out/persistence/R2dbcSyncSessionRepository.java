package com.yowyob.tiibntick.core.sync.adapter.out.persistence;

import com.yowyob.tiibntick.core.sync.adapter.out.persistence.entity.SyncSessionEntity;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncSessionRepository;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSession;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSessionId;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public class R2dbcSyncSessionRepository implements ISyncSessionRepository {

    private final R2dbcEntityTemplate template;

    public R2dbcSyncSessionRepository(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<Void> save(SyncSession session) {
        SyncSessionEntity entity = SyncSessionEntity.fromDomain(session);
        return template.exists(Query.query(Criteria.where("id").is(entity.id)), SyncSessionEntity.class)
                .flatMap(exists -> exists
                        ? template.update(entity).then()
                        : template.insert(entity).then());
    }

    @Override
    public Mono<SyncSession> findById(SyncSessionId id) {
        return template.selectOne(
                Query.query(Criteria.where("id").is(id.value())),
                SyncSessionEntity.class)
                .map(SyncSessionEntity::toDomain);
    }

    @Override
    public Flux<SyncSession> findRecentByUser(String userId, String tenantId, int limit) {
        return template.select(
                Query.query(Criteria.where("user_id").is(userId)
                        .and("tenant_id").is(tenantId))
                        .limit(limit),
                SyncSessionEntity.class)
                .map(SyncSessionEntity::toDomain);
    }

    @Override
    public Mono<Long> deleteCompletedBefore(LocalDateTime cutoff) {
        return template.delete(
                Query.query(Criteria.where("completed_at").lessThan(cutoff)
                        .and("status").in("COMPLETED", "PARTIAL", "FAILED")),
                SyncSessionEntity.class);
    }
}
