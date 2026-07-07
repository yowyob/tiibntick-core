package com.yowyob.tiibntick.core.geo.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.adapter.out.persistence.entity.RoadArcEntity;
import com.yowyob.tiibntick.core.geo.application.port.out.IRoadArcRepository;
import com.yowyob.tiibntick.core.geo.domain.model.RoadArc;
import com.yowyob.tiibntick.core.geo.domain.model.RoadArcId;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC implementation of {@link IRoadArcRepository}.
 *
 * Author: MANFOUO Braun
 */
@Repository
public class R2dbcRoadArcRepository implements IRoadArcRepository {

    private final R2dbcEntityTemplate template;

    public R2dbcRoadArcRepository(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<RoadArc> save(RoadArc arc) {
        RoadArcEntity entity = RoadArcEntity.fromDomain(arc);
        return template.exists(
                        Query.query(Criteria.where("id").is(entity.getId())
                                .and("tenant_id").is(entity.getTenantId())),
                        RoadArcEntity.class)
                .flatMap(exists -> exists
                        ? template.update(entity).thenReturn(entity)
                        : template.insert(entity))
                .map(RoadArcEntity::toDomain);
    }

    @Override
    public Mono<RoadArc> findById(RoadArcId id, UUID tenantId) {
        Criteria criteria = Criteria.where("id").is(id.value());
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        return template.selectOne(Query.query(criteria), RoadArcEntity.class)
                .map(RoadArcEntity::toDomain);
    }

    @Override
    public Flux<RoadArc> findAllByTenant(UUID tenantId) {
        return template.select(
                        Query.query(Criteria.where("tenant_id").is(tenantId)),
                        RoadArcEntity.class)
                .map(RoadArcEntity::toDomain);
    }

    @Override
    public Flux<RoadArc> findBySourceId(RoadNodeId sourceId, UUID tenantId) {
        Criteria criteria = Criteria.where("source_id").is(sourceId.value());
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        return template.select(Query.query(criteria), RoadArcEntity.class)
                .map(RoadArcEntity::toDomain);
    }

    @Override
    public Flux<RoadArc> findByTargetId(RoadNodeId targetId, UUID tenantId) {
        Criteria criteria = Criteria.where("target_id").is(targetId.value());
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        return template.select(Query.query(criteria), RoadArcEntity.class)
                .map(RoadArcEntity::toDomain);
    }

    @Override
    public Mono<RoadArc> updateTrafficFactor(RoadArcId id, UUID tenantId, double trafficFactor) {
        return template.update(
                        Query.query(Criteria.where("id").is(id.value()).and("tenant_id").is(tenantId)),
                        Update.update("traffic_factor", trafficFactor)
                                .set("updated_at", Instant.now()),
                        RoadArcEntity.class)
                .flatMap(count -> count > 0
                        ? findById(id, tenantId)
                        : Mono.empty());
    }

    @Override
    public Mono<Void> deleteById(RoadArcId id, UUID tenantId) {
        return template.delete(
                Query.query(Criteria.where("id").is(id.value()).and("tenant_id").is(tenantId)),
                RoadArcEntity.class
        ).then();
    }
}
