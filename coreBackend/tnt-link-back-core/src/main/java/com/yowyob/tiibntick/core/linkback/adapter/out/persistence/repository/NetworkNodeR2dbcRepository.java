package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.NetworkNodeEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NetworkNodeR2dbcRepository extends ReactiveCrudRepository<NetworkNodeEntity, UUID> {

    /**
     * Phase 0 stop-gap (audit n6 S25, Chantier G — see docs/audits/remediation/phase-0-critical.md):
     * {@code /nearby} had no result cap, so a wide/borderless bounding box could return the
     * entire tenant's node table in one call. Bounding the {@code SELECT} itself with {@code LIMIT}
     * (not an in-memory filter after the fact) closes the scraping/DoS-lite exposure until the
     * Phase 1 geohash-tile + BFF redesign replaces this endpoint shape entirely.
     */
    int MAX_NEARBY_RESULTS = 100;

    Mono<NetworkNodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<NetworkNodeEntity> findByTenantIdAndRefId(UUID tenantId, UUID refId);

    @Query("SELECT * FROM tnt_link.network_nodes WHERE tenant_id = :tenantId "
            + "AND latitude BETWEEN :minLat AND :maxLat "
            + "AND longitude BETWEEN :minLng AND :maxLng "
            + "LIMIT " + MAX_NEARBY_RESULTS)
    Flux<NetworkNodeEntity> findWithinBoundingBox(UUID tenantId, double minLat, double maxLat, double minLng, double maxLng);

    @Query("SELECT * FROM tnt_link.network_nodes WHERE tenant_id = :tenantId "
            + "ORDER BY trust_score DESC, gamification_level DESC LIMIT :limit")
    Flux<NetworkNodeEntity> findTopRanked(UUID tenantId, int limit);
}
