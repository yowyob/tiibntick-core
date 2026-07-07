package com.yowyob.tiibntick.core.resource.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity.ResourceAllocationEntity;
import com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository.ResourceAllocationR2dbcRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceAllocationRepository;
import com.yowyob.tiibntick.core.resource.domain.model.AllocationStatus;
import com.yowyob.tiibntick.core.resource.domain.model.ResourceAllocation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Driven adapter: bridges the ResourceAllocationRepository port to the R2DBC repository.
 * @author MANFOUO Braun.
 */
@Component
public class ResourceAllocationRepositoryAdapter implements ResourceAllocationRepository {

    private final ResourceAllocationR2dbcRepository r2dbcRepository;

    public ResourceAllocationRepositoryAdapter(ResourceAllocationR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    @Override
    public Mono<ResourceAllocation> save(ResourceAllocation allocation) {
        var _entity = ResourceAllocationEntity.fromDomain(allocation);
        return r2dbcRepository.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbcRepository.save(_entity);
                })
                .map(ResourceAllocationEntity::toDomain);
    }

    @Override
    public Mono<ResourceAllocation> findById(UUID allocationId) {
        return r2dbcRepository.findById(allocationId)
                .map(ResourceAllocationEntity::toDomain);
    }

    @Override
    public Flux<ResourceAllocation> findByResourceId(UUID tenantId, UUID resourceId) {
        return r2dbcRepository.findByTenantIdAndResourceId(tenantId, resourceId)
                .map(ResourceAllocationEntity::toDomain);
    }

    @Override
    public Mono<ResourceAllocation> findActiveByResource(UUID tenantId, UUID resourceId) {
        return r2dbcRepository.findActiveByTenantIdAndResourceId(tenantId, resourceId)
                .map(ResourceAllocationEntity::toDomain);
    }

    @Override
    public Flux<ResourceAllocation> findByUserAndStatus(UUID tenantId, UUID userId, AllocationStatus status) {
        return r2dbcRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, status.name())
                .map(ResourceAllocationEntity::toDomain);
    }
}
