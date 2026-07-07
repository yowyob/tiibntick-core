package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryPersonEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code DeliveryPersonEntity}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcDeliveryPersonRepository
        extends ReactiveCrudRepository<DeliveryPersonEntity, UUID> {

    @Query("SELECT * FROM tnt_delivery_persons WHERE tenant_id = :tenantId AND id = :id")
    Mono<DeliveryPersonEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT * FROM tnt_delivery_persons WHERE tenant_id = :tenantId AND actor_id = :actorId")
    Mono<DeliveryPersonEntity> findByTenantIdAndActorId(UUID tenantId, UUID actorId);

    @Query("SELECT * FROM tnt_delivery_persons WHERE tenant_id = :tenantId AND status = :status")
    Flux<DeliveryPersonEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    /**
     * Finds available delivery persons within a geographic bounding box.
     * Requires PostGIS extension or coordinate-based approximation.
     * Uses simple bounding box: ±(radiusKm/111) degrees latitude, adjusted longitude.
     */
    @Query("""
            SELECT * FROM tnt_delivery_persons
            WHERE tenant_id = :tenantId
              AND status = 'APPROVED'
              AND current_latitude  BETWEEN :minLat AND :maxLat
              AND current_longitude BETWEEN :minLon AND :maxLon
            ORDER BY total_deliveries DESC
            LIMIT 20
            """)
    Flux<DeliveryPersonEntity> findAvailableNear(UUID tenantId,
                                                  double minLat, double maxLat,
                                                  double minLon, double maxLon);
}
