package com.yowyob.tiibntick.core.sync.adapter.out.persistence;

import com.yowyob.tiibntick.core.sync.adapter.out.persistence.entity.OfflineOperationEntity;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationRepository;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpStatus;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class R2dbcOfflineOperationRepository implements IOfflineOperationRepository {

    private final R2dbcEntityTemplate template;

    public R2dbcOfflineOperationRepository(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<Void> save(OfflineOperation operation) {
        OfflineOperationEntity entity = OfflineOperationEntity.fromDomain(operation);
        return template.exists(Query.query(Criteria.where("id").is(entity.id)), OfflineOperationEntity.class)
                .flatMap(exists -> exists
                        ? template.update(entity).then()
                        : template.insert(entity).then());
    }

    @Override
    public Mono<Void> saveAll(List<OfflineOperation> operations) {
        return Flux.fromIterable(operations)
                .concatMap(this::save)
                .then();
    }

    @Override
    public Flux<OfflineOperation> findPendingByUser(String userId, String tenantId) {
        return template.select(
                Query.query(Criteria.where("user_id").is(userId)
                        .and("tenant_id").is(tenantId)
                        .and("status").in("QUEUED", "FAILED")),
                OfflineOperationEntity.class)
                .map(OfflineOperationEntity::toDomain);
    }

    @Override
    public Flux<OfflineOperation> findBySessionId(String sessionId) {
        return Flux.empty();
    }

    @Override
    public Mono<Void> updateStatus(String operationId, OfflineOpStatus status, String error) {
        return template.update(
                Query.query(Criteria.where("id").is(operationId)),
                Update.update("status", status.name())
                        .set("error", error)
                        .set("last_attempt_at", LocalDateTime.now()),
                OfflineOperationEntity.class)
                .then();
    }
}
