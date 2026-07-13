package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.NetworkAlertEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NetworkAlertR2dbcRepository extends ReactiveCrudRepository<NetworkAlertEntity, UUID> {

    Mono<NetworkAlertEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<NetworkAlertEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("SELECT * FROM tnt_link.network_alerts WHERE tenant_id = :tenantId AND status = :status "
            + "AND latitude BETWEEN :minLat AND :maxLat "
            + "AND longitude BETWEEN :minLng AND :maxLng")
    Flux<NetworkAlertEntity> findByTenantIdAndStatusWithinBoundingBox(
            UUID tenantId, String status, double minLat, double maxLat, double minLng, double maxLng);
}
