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

    // Bounding box computed per-row from each zone's own center/radius — a cheap DB-side
    // pre-filter (superset of the exact circle); the caller still applies the precise
    // haversine containment check on the narrowed candidate set.
    @Query("SELECT * FROM tnt_link.dao_zones WHERE tenant_id = :tenantId AND status = :status "
            + "AND :lat BETWEEN (center_latitude - radius_km / 111.0) AND (center_latitude + radius_km / 111.0) "
            + "AND :lng BETWEEN (center_longitude - radius_km / (111.0 * COS(RADIANS(center_latitude)))) "
            + "AND (center_longitude + radius_km / (111.0 * COS(RADIANS(center_latitude))))")
    Flux<DaoZoneEntity> findByTenantIdAndStatusContainingPoint(UUID tenantId, String status, double lat, double lng);
}
