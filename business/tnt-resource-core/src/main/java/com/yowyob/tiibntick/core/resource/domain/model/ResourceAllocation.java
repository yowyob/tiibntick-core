package com.yowyob.tiibntick.core.resource.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root tracking the assignment of a vehicle or equipment to a user/mission.
 * Acts as an audit log of resource allocations across the TiiBnTick fleet.
 *
 * @author MANFOUO Braun.
 */
public final class ResourceAllocation {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final UUID resourceId;
    private final ResourceType resourceType;
    private final UUID assignedToUserId;
    private final UUID missionId;
    private final AllocationStatus status;
    private final Instant allocatedAt;
    private final Instant releasedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ResourceAllocation(UUID id, UUID tenantId, UUID agencyId, UUID resourceId,
            ResourceType resourceType, UUID assignedToUserId, UUID missionId,
            AllocationStatus status, Instant allocatedAt, Instant releasedAt,
            Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.agencyId = Objects.requireNonNull(agencyId, "agencyId is required");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId is required");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType is required");
        this.assignedToUserId = Objects.requireNonNull(assignedToUserId, "assignedToUserId is required");
        this.missionId = missionId;
        this.status = Objects.requireNonNull(status, "status is required");
        this.allocatedAt = Objects.requireNonNull(allocatedAt, "allocatedAt is required");
        this.releasedAt = releasedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static ResourceAllocation allocate(UUID tenantId, UUID agencyId, UUID resourceId,
            ResourceType resourceType, UUID assignedToUserId, UUID missionId) {
        Instant now = Instant.now();
        return new ResourceAllocation(UUID.randomUUID(), tenantId, agencyId, resourceId,
                resourceType, assignedToUserId, missionId, AllocationStatus.ACTIVE, now, null, now, now);
    }

    public static ResourceAllocation rehydrate(UUID id, UUID tenantId, UUID agencyId, UUID resourceId,
            ResourceType resourceType, UUID assignedToUserId, UUID missionId, AllocationStatus status,
            Instant allocatedAt, Instant releasedAt, Instant createdAt, Instant updatedAt) {
        return new ResourceAllocation(id, tenantId, agencyId, resourceId, resourceType,
                assignedToUserId, missionId, status, allocatedAt, releasedAt, createdAt, updatedAt);
    }

    public ResourceAllocation release() {
        if (status != AllocationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot release allocation in status: " + status);
        }
        Instant now = Instant.now();
        return new ResourceAllocation(id, tenantId, agencyId, resourceId, resourceType,
                assignedToUserId, missionId, AllocationStatus.RELEASED, allocatedAt, now, createdAt, now);
    }

    public ResourceAllocation expire() {
        if (status != AllocationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot expire allocation in status: " + status);
        }
        Instant now = Instant.now();
        return new ResourceAllocation(id, tenantId, agencyId, resourceId, resourceType,
                assignedToUserId, missionId, AllocationStatus.EXPIRED, allocatedAt, now, createdAt, now);
    }

    public boolean isActive() { return status == AllocationStatus.ACTIVE; }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID agencyId() { return agencyId; }
    public UUID resourceId() { return resourceId; }
    public ResourceType resourceType() { return resourceType; }
    public UUID assignedToUserId() { return assignedToUserId; }
    public UUID missionId() { return missionId; }
    public AllocationStatus status() { return status; }
    public Instant allocatedAt() { return allocatedAt; }
    public Instant releasedAt() { return releasedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
