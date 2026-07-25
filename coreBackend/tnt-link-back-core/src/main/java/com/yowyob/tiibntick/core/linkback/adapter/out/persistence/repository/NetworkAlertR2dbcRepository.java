package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.NetworkAlertEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NetworkAlertR2dbcRepository extends ReactiveCrudRepository<NetworkAlertEntity, UUID> {

    /**
     * Hard cap on {@code /nearby} results (Audit n6 S25, Chantier G). Since 007-add-spatial-gist-indexes,
     * {@link #findByTenantIdAndStatusWithinBoundingBox} is served by the GIST index on
     * {@code ST_MakePoint(longitude, latitude)::geography} rather than a sequential bbox scan —
     * this LIMIT is now a sane result-size cap, not the scalability mechanism itself.
     */
    int MAX_NEARBY_RESULTS = 100;

    Mono<NetworkAlertEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<NetworkAlertEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("SELECT * FROM tnt_link.network_alerts WHERE tenant_id = :tenantId AND status = :status "
            + "AND ST_Intersects("
            + "ST_MakePoint(longitude, latitude)::geography, "
            + "ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)::geography) "
            + "LIMIT " + MAX_NEARBY_RESULTS)
    Flux<NetworkAlertEntity> findByTenantIdAndStatusWithinBoundingBox(
            UUID tenantId, String status, double minLat, double maxLat, double minLng, double maxLng);
}
