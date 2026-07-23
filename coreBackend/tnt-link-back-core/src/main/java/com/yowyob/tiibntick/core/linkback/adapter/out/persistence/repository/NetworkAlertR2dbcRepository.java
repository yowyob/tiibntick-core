package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.NetworkAlertEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NetworkAlertR2dbcRepository extends ReactiveCrudRepository<NetworkAlertEntity, UUID> {

    /**
     * Phase 0 stop-gap (audit n6 S25, Chantier G — see docs/audits/remediation/phase-0-critical.md):
     * {@code /nearby} had no result cap, so a wide radius could return the entire tenant's active
     * alert table in one call. Bounding the {@code SELECT} itself with {@code LIMIT} (not an
     * in-memory filter after the fact) closes the scraping/DoS-lite exposure until the Phase 1
     * geohash-tile + BFF redesign replaces this endpoint shape entirely.
     */
    int MAX_NEARBY_RESULTS = 100;

    Mono<NetworkAlertEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<NetworkAlertEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("SELECT * FROM tnt_link.network_alerts WHERE tenant_id = :tenantId AND status = :status "
            + "AND latitude BETWEEN :minLat AND :maxLat "
            + "AND longitude BETWEEN :minLng AND :maxLng "
            + "LIMIT " + MAX_NEARBY_RESULTS)
    Flux<NetworkAlertEntity> findByTenantIdAndStatusWithinBoundingBox(
            UUID tenantId, String status, double minLat, double maxLat, double minLng, double maxLng);
}
