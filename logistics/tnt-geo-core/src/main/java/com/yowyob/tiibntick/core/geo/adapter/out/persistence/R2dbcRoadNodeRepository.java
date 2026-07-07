package com.yowyob.tiibntick.core.geo.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.adapter.out.persistence.entity.RoadNodeEntity;
import com.yowyob.tiibntick.core.geo.application.port.out.IRoadNodeRepository;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNode;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC implementation of {@link IRoadNodeRepository}.
 * PostGIS spatial queries use {@link DatabaseClient} with native SQL
 * leveraging the GiST spatial index on the geography column.
 *
 * Author: MANFOUO Braun
 */
@Repository
public class R2dbcRoadNodeRepository implements IRoadNodeRepository {

    //private static final String SCHEMA = "tnt_geography";
    //private static final String TABLE = "road_nodes";

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;

    public R2dbcRoadNodeRepository(R2dbcEntityTemplate template, DatabaseClient databaseClient) {
        this.template = template;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<RoadNode> save(RoadNode node) {
        RoadNodeEntity entity = RoadNodeEntity.fromDomain(node);
        return template.exists(
                        Query.query(Criteria.where("id").is(entity.getId())
                                .and("tenant_id").is(entity.getTenantId())),
                        RoadNodeEntity.class)
                .flatMap(exists -> {
                    if (exists) {
                        return template.update(entity).thenReturn(entity);
                    }
                    return template.insert(entity);
                })
                .map(RoadNodeEntity::toDomain);
    }

    @Override
    public Mono<RoadNode> findById(RoadNodeId id, UUID tenantId) {
        Criteria criteria = Criteria.where("id").is(id.value());
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        return template.selectOne(Query.query(criteria), RoadNodeEntity.class)
                .map(RoadNodeEntity::toDomain);
    }

    @Override
    public Flux<RoadNode> findAllByTenant(UUID tenantId) {
        return template.select(
                        Query.query(Criteria.where("tenant_id").is(tenantId)),
                        RoadNodeEntity.class)
                .map(RoadNodeEntity::toDomain);
    }

    @Override
    public Flux<RoadNode> findByCityCode(UUID tenantId, String cityCode) {
        return template.select(
                        Query.query(Criteria.where("tenant_id").is(tenantId)
                                .and("city_code").is(cityCode.toUpperCase())
                                .and("is_active").is(true)),
                        RoadNodeEntity.class)
                .map(RoadNodeEntity::toDomain);
    }

    /**
     * PostGIS ST_DWithin spatial query with GiST index.
     * Converts radiusKm to metres for the geography type (unit: metres when using geography).
     */
    @Override
    public Flux<RoadNode> findWithinRadius(UUID tenantId, GeoPoint center, double radiusKm) {
        double radiusMetres = radiusKm * 1000.0;
        String sql = String.format(java.util.Locale.ROOT, """
                SELECT id, tenant_id, type, latitude, longitude, name, city_code,
                       is_active, capacity_slots, created_at, updated_at
                FROM tnt_geography.road_nodes
                WHERE tenant_id = :tenantId
                  AND is_active = true
                  AND ST_DWithin(
                        ST_MakePoint(longitude, latitude)::geography,
                        ST_MakePoint(%.8f, %.8f)::geography,
                        %.2f
                      )
                ORDER BY ST_Distance(
                    ST_MakePoint(longitude, latitude)::geography,
                    ST_MakePoint(%.8f, %.8f)::geography
                )
                """, center.longitude(), center.latitude(), radiusMetres,
                     center.longitude(), center.latitude());
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .map(row -> {
                    RoadNodeEntity e = new RoadNodeEntity();
                    e.setId(row.get("id", String.class));
                    e.setTenantId(row.get("tenant_id", UUID.class));
                    e.setType(row.get("type", String.class));
                    e.setLatitude(row.get("latitude", Double.class));
                    e.setLongitude(row.get("longitude", Double.class));
                    e.setName(row.get("name", String.class));
                    e.setCityCode(row.get("city_code", String.class));
                    e.setActive(Boolean.TRUE.equals(row.get("is_active", Boolean.class)));
                    e.setCapacitySlots(row.get("capacity_slots", Integer.class));
                    e.setCreatedAt(row.get("created_at", Instant.class));
                    e.setUpdatedAt(row.get("updated_at", Instant.class));
                    return e;
                })
                .all()
                .map(RoadNodeEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(RoadNodeId id, UUID tenantId) {
        Criteria criteria = Criteria.where("id").is(id.value());
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        return template.exists(Query.query(criteria), RoadNodeEntity.class);
    }

    @Override
    public Mono<Void> deleteById(RoadNodeId id, UUID tenantId) {
        return template.update(
                Query.query(Criteria.where("id").is(id.value()).and("tenant_id").is(tenantId)),
                Update.update("is_active", false).set("updated_at", Instant.now()),
                RoadNodeEntity.class
        ).then();
    }
}
