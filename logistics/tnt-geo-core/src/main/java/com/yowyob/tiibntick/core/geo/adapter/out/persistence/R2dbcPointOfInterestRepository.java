package com.yowyob.tiibntick.core.geo.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.adapter.out.persistence.entity.PointOfInterestEntity;
import com.yowyob.tiibntick.core.geo.application.port.out.IPointOfInterestRepository;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.PointOfInterest;
import com.yowyob.tiibntick.core.geo.domain.model.PoiType;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC implementation of {@link IPointOfInterestRepository}.
 *
 * Author: MANFOUO Braun
 */
@Repository
public class R2dbcPointOfInterestRepository implements IPointOfInterestRepository {

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;

    public R2dbcPointOfInterestRepository(R2dbcEntityTemplate template,
                                          DatabaseClient databaseClient) {
        this.template = template;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<PointOfInterest> save(PointOfInterest poi) {
        PointOfInterestEntity entity = PointOfInterestEntity.fromDomain(poi);
        return template.exists(
                        Query.query(Criteria.where("id").is(entity.getId())),
                        PointOfInterestEntity.class)
                .flatMap(exists -> exists
                        ? template.update(entity).thenReturn(entity)
                        : template.insert(entity))
                .map(PointOfInterestEntity::toDomain);
    }

    @Override
    public Mono<PointOfInterest> findById(UUID id, UUID tenantId) {
        Criteria criteria = Criteria.where("id").is(id);
        if (tenantId != null) criteria = criteria.and("tenant_id").is(tenantId);
        return template.selectOne(Query.query(criteria), PointOfInterestEntity.class)
                .map(PointOfInterestEntity::toDomain);
    }

    @Override
    public Flux<PointOfInterest> findByCityCode(UUID tenantId, String cityCode) {
        Criteria criteria = Criteria.where("city_code").is(cityCode);
        if (tenantId != null) criteria = criteria.and("tenant_id").is(tenantId);
        return template.select(Query.query(criteria), PointOfInterestEntity.class)
                .map(PointOfInterestEntity::toDomain);
    }

    @Override
    public Flux<PointOfInterest> findByType(UUID tenantId, PoiType type) {
        Criteria criteria = Criteria.where("type").is(type.name());
        if (tenantId != null) criteria = criteria.and("tenant_id").is(tenantId);
        return template.select(Query.query(criteria), PointOfInterestEntity.class)
                .map(PointOfInterestEntity::toDomain);
    }

    @Override
    public Flux<PointOfInterest> findWithinRadius(UUID tenantId, GeoPoint center, double radiusKm) {
        double radiusMetres = radiusKm * 1000.0;
        String tenantFilter = tenantId != null ? "AND tenant_id = :tenantId" : "";
        String sql = String.format(java.util.Locale.ROOT, """
                SELECT id, tenant_id, name, type, latitude, longitude,
                       description, city_code, is_verified, created_at, updated_at
                FROM tnt_geography.points_of_interest
                WHERE 1=1 %s
                  AND ST_DWithin(
                        ST_MakePoint(longitude, latitude)::geography,
                        ST_MakePoint(%.8f, %.8f)::geography,
                        %.2f
                      )
                ORDER BY ST_Distance(
                    ST_MakePoint(longitude, latitude)::geography,
                    ST_MakePoint(%.8f, %.8f)::geography
                )
                """, tenantFilter, center.longitude(), center.latitude(), radiusMetres,
                     center.longitude(), center.latitude());

        var spec = databaseClient.sql(sql);
        if (tenantId != null) spec = spec.bind("tenantId", tenantId);

        return spec.map(row -> {
            PointOfInterestEntity e = new PointOfInterestEntity();
            e.setId(row.get("id", UUID.class));
            e.setTenantId(row.get("tenant_id", UUID.class));
            e.setName(row.get("name", String.class));
            e.setType(row.get("type", String.class));
            e.setLatitude(row.get("latitude", Double.class));
            e.setLongitude(row.get("longitude", Double.class));
            e.setDescription(row.get("description", String.class));
            e.setCityCode(row.get("city_code", String.class));
            e.setVerified(Boolean.TRUE.equals(row.get("is_verified", Boolean.class)));
            e.setCreatedAt(row.get("created_at", Instant.class));
            e.setUpdatedAt(row.get("updated_at", Instant.class));
            return e;
        }).all().map(PointOfInterestEntity::toDomain);
    }

    @Override
    public Flux<PointOfInterest> findVerifiedByCity(UUID tenantId, String cityCode) {
        Criteria criteria = Criteria.where("city_code").is(cityCode).and("is_verified").is(true);
        if (tenantId != null) criteria = criteria.and("tenant_id").is(tenantId);
        return template.select(Query.query(criteria), PointOfInterestEntity.class)
                .map(PointOfInterestEntity::toDomain);
    }
}
