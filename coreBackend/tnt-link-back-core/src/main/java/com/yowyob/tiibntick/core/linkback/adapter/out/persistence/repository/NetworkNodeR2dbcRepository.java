package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.NetworkNodeEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NetworkNodeR2dbcRepository extends ReactiveCrudRepository<NetworkNodeEntity, UUID> {

    Mono<NetworkNodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<NetworkNodeEntity> findByTenantIdAndRefId(UUID tenantId, UUID refId);

    @Query("SELECT * FROM tnt_link.network_nodes WHERE tenant_id = :tenantId "
            + "AND latitude BETWEEN :minLat AND :maxLat "
            + "AND longitude BETWEEN :minLng AND :maxLng")
    Flux<NetworkNodeEntity> findWithinBoundingBox(UUID tenantId, double minLat, double maxLat, double minLng, double maxLng);

    @Query("SELECT * FROM tnt_link.network_nodes WHERE tenant_id = :tenantId "
            + "ORDER BY trust_score DESC, gamification_level DESC LIMIT :limit")
    Flux<NetworkNodeEntity> findTopRanked(UUID tenantId, int limit);
}
