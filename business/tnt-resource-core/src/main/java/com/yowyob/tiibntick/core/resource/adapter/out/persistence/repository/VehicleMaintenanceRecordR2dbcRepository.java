package com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.VehicleMaintenanceRecordEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for VehicleMaintenanceRecordEntity.
 * @author MANFOUO Braun.
 */
public interface VehicleMaintenanceRecordR2dbcRepository extends ReactiveCrudRepository<VehicleMaintenanceRecordEntity, UUID> {

    @Query("SELECT * FROM tnt_vehicle_maintenance_records WHERE tenant_id = :tenantId AND vehicle_id = :vehicleId ORDER BY created_at DESC")
    Flux<VehicleMaintenanceRecordEntity> findByTenantIdAndVehicleId(UUID tenantId, UUID vehicleId);
}
