package com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.EquipmentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for EquipmentEntity.
 * @author MANFOUO Braun.
 */
public interface EquipmentR2dbcRepository extends ReactiveCrudRepository<EquipmentEntity, UUID> {

    @Query("SELECT * FROM tnt_equipment WHERE tenant_id = :tenantId AND id = :equipmentId")
    Mono<EquipmentEntity> findByTenantIdAndId(UUID tenantId, UUID equipmentId);

    @Query("SELECT * FROM tnt_equipment WHERE tenant_id = :tenantId AND branch_id = :branchId ORDER BY created_at ASC")
    Flux<EquipmentEntity> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);

    @Query("SELECT * FROM tnt_equipment WHERE tenant_id = :tenantId AND branch_id = :branchId AND status = :status")
    Flux<EquipmentEntity> findByTenantIdAndBranchIdAndStatus(UUID tenantId, UUID branchId, String status);

    @Query("SELECT EXISTS(SELECT 1 FROM tnt_equipment WHERE tenant_id = :tenantId AND serial_number = :serialNumber)")
    Mono<Boolean> existsByTenantIdAndSerialNumber(UUID tenantId, String serialNumber);
}
