package com.yowyob.tiibntick.core.resource.application.port.out;

import com.yowyob.tiibntick.core.resource.domain.model.ResourceAllocation;
import com.yowyob.tiibntick.core.resource.domain.model.AllocationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port: persistence contract for ResourceAllocation aggregate.
 * @author MANFOUO Braun.
 */
public interface ResourceAllocationRepository {

    Mono<ResourceAllocation> save(ResourceAllocation allocation);

    Mono<ResourceAllocation> findById(UUID allocationId);

    Flux<ResourceAllocation> findByResourceId(UUID tenantId, UUID resourceId);

    Mono<ResourceAllocation> findActiveByResource(UUID tenantId, UUID resourceId);

    Flux<ResourceAllocation> findByUserAndStatus(UUID tenantId, UUID userId, AllocationStatus status);
}
