package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.DaoZoneEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DaoZoneR2dbcRepository extends ReactiveCrudRepository<DaoZoneEntity, UUID> {

    Mono<DaoZoneEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<DaoZoneEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    // Exact geodesic circle containment via the GIST index on center_longitude/center_latitude
    // (007-add-spatial-gist-indexes.sql) — ST_DWithin replaces the old lat/lng-box approximation,
    // which was both slower (sequential scan) and imprecise (rectangle superset of the real circle).
    @Query("SELECT * FROM tnt_link.dao_zones WHERE tenant_id = :tenantId AND status = :status "
            + "AND ST_DWithin("
            + "ST_MakePoint(center_longitude, center_latitude)::geography, "
            + "ST_MakePoint(:lng, :lat)::geography, "
            + "radius_km * 1000)")
    Flux<DaoZoneEntity> findByTenantIdAndStatusContainingPoint(UUID tenantId, String status, double lat, double lng);
}
