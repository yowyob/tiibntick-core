package com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.VehicleEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for VehicleEntity.
 * @author MANFOUO Braun.
 */
public interface VehicleR2dbcRepository extends ReactiveCrudRepository<VehicleEntity, UUID> {

    @Query("SELECT * FROM tnt_vehicles WHERE tenant_id = :tenantId AND id = :vehicleId")
    Mono<VehicleEntity> findByTenantIdAndId(UUID tenantId, UUID vehicleId);

    @Query("SELECT * FROM tnt_vehicles WHERE tenant_id = :tenantId AND agency_id = :agencyId ORDER BY created_at ASC")
    Flux<VehicleEntity> findByTenantIdAndAgencyId(UUID tenantId, UUID agencyId);

    @Query("SELECT * FROM tnt_vehicles WHERE tenant_id = :tenantId AND agency_id = :agencyId AND status = :status ORDER BY created_at ASC")
    Flux<VehicleEntity> findByTenantIdAndAgencyIdAndStatus(UUID tenantId, UUID agencyId, String status);

    @Query("SELECT * FROM tnt_vehicles WHERE tenant_id = :tenantId AND agency_id = :agencyId AND status = 'AVAILABLE'")
    Flux<VehicleEntity> findAvailableByTenantIdAndAgencyId(UUID tenantId, UUID agencyId);

    @Query("SELECT EXISTS(SELECT 1 FROM tnt_vehicles WHERE tenant_id = :tenantId AND registration_number = :registrationNumber)")
    Mono<Boolean> existsByTenantIdAndRegistrationNumber(UUID tenantId, String registrationNumber);

    @Query("SELECT COUNT(*) FROM tnt_vehicles WHERE tenant_id = :tenantId AND agency_id = :agencyId AND status = :status")
    Mono<Long> countByTenantIdAndAgencyIdAndStatus(UUID tenantId, UUID agencyId, String status);

    /**
     * Finds a vehicle by ID without tenant scoping.
     * For tnt-incident-core cross-module calls where only vehicleId is available.
     */
    @Query("SELECT * FROM tnt_vehicles WHERE id = :vehicleId LIMIT 1")
    Mono<VehicleEntity> findById(UUID vehicleId);

    /**
     * Finds AVAILABLE vehicles with sufficient capacity, optionally filtered by agency,
     * sorted by haversine distance from a GPS coordinate.
     * Used for replacement driver matching by tnt-incident-core.
     */
    @Query("""
            SELECT * FROM tnt_vehicles
            WHERE tenant_id = :tenantId
              AND status = 'AVAILABLE'
              AND (:agencyId IS NULL OR agency_id = :agencyId)
              AND max_weight_kg >= :requiredCapacityKg
              AND (:vehicleCategory IS NULL OR type = :vehicleCategory)
              AND gps_latitude IS NOT NULL
              AND gps_longitude IS NOT NULL
            ORDER BY
              (6371 * acos(
                  cos(radians(:latitude)) * cos(radians(gps_latitude)) *
                  cos(radians(gps_longitude) - radians(:longitude)) +
                  sin(radians(:latitude)) * sin(radians(gps_latitude))
              )) ASC
            LIMIT 15
            """)
    Flux<VehicleEntity> findAvailableNear(UUID tenantId, UUID agencyId,
                                           double latitude, double longitude,
                                           double requiredCapacityKg, String vehicleCategory);
}
