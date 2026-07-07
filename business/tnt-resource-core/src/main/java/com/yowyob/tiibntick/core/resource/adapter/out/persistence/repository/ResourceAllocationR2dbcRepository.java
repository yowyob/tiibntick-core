package com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.ResourceAllocationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for ResourceAllocationEntity.
 * @author MANFOUO Braun.
 */
public interface ResourceAllocationR2dbcRepository extends ReactiveCrudRepository<ResourceAllocationEntity, UUID> {

    @Query("SELECT * FROM tnt_resource_allocations WHERE tenant_id = :tenantId AND resource_id = :resourceId ORDER BY allocated_at DESC")
    Flux<ResourceAllocationEntity> findByTenantIdAndResourceId(UUID tenantId, UUID resourceId);

    @Query("SELECT * FROM tnt_resource_allocations WHERE tenant_id = :tenantId AND resource_id = :resourceId AND status = 'ACTIVE' LIMIT 1")
    Mono<ResourceAllocationEntity> findActiveByTenantIdAndResourceId(UUID tenantId, UUID resourceId);

    @Query("SELECT * FROM tnt_resource_allocations WHERE tenant_id = :tenantId AND assigned_to_user_id = :userId AND status = :status")
    Flux<ResourceAllocationEntity> findByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, String status);
}
