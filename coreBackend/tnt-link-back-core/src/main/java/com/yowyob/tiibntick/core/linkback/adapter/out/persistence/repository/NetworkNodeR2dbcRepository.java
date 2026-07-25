package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.NetworkNodeEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface NetworkNodeR2dbcRepository extends ReactiveCrudRepository<NetworkNodeEntity, UUID> {

    /**
     * Hard cap on {@code /nearby} results (Audit n6 S25, Chantier G). Since 007-add-spatial-gist-indexes,
     * {@link #findWithinBoundingBox} is served by the GIST index on {@code ST_MakePoint(longitude,
     * latitude)::geography} (see {@code 007-add-spatial-gist-indexes.sql} and tnt-geo-core's
     * {@code road_nodes} for the same pattern) rather than a sequential bbox scan — this LIMIT is now
     * a sane result-size cap, not the scalability mechanism itself.
     */
    int MAX_NEARBY_RESULTS = 100;

    Mono<NetworkNodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<NetworkNodeEntity> findByTenantIdAndRefId(UUID tenantId, UUID refId);

    /** Single {@code IN (...)} round trip for {@code /by-ref/batch} (Audit n6 S27) — replaces
     *  the N+1 {@code flatMap} over {@link #findByTenantIdAndRefId} per requested id. */
    Flux<NetworkNodeEntity> findByTenantIdAndRefIdIn(UUID tenantId, Collection<UUID> refIds);

    @Query("SELECT * FROM tnt_link.network_nodes WHERE tenant_id = :tenantId "
            + "AND ST_Intersects("
            + "ST_MakePoint(longitude, latitude)::geography, "
            + "ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)::geography) "
            + "LIMIT " + MAX_NEARBY_RESULTS)
    Flux<NetworkNodeEntity> findWithinBoundingBox(UUID tenantId, double minLat, double maxLat, double minLng, double maxLng);

    @Query("SELECT * FROM tnt_link.network_nodes WHERE tenant_id = :tenantId "
            + "ORDER BY trust_score DESC, gamification_level DESC LIMIT :limit")
    Flux<NetworkNodeEntity> findTopRanked(UUID tenantId, int limit);
}
