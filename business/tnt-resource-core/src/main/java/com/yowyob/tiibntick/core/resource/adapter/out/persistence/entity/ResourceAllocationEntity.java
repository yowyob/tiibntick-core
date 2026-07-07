package com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.resource.domain.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for ResourceAllocation.
 * Maps to the {@code tnt_resource_allocations} table.
 *
 * @author MANFOUO Braun.
 */
@Table("tnt_resource_allocations")
public class ResourceAllocationEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("tenant_id")           private UUID tenantId;
    @Column("agency_id")           private UUID agencyId;
    @Column("resource_id")         private UUID resourceId;
    @Column("resource_type")       private String resourceType;
    @Column("assigned_to_user_id") private UUID assignedToUserId;
    @Column("mission_id")          private UUID missionId;
    @Column("status")              private String status;
    @Column("allocated_at")        private Instant allocatedAt;
    @Column("released_at")         private Instant releasedAt;
    @Column("created_at")          private Instant createdAt;
    @Column("updated_at")          private Instant updatedAt;

    public static ResourceAllocationEntity fromDomain(ResourceAllocation allocation) {
        ResourceAllocationEntity e = new ResourceAllocationEntity();
        e.id = allocation.id();
        e.tenantId = allocation.tenantId();
        e.agencyId = allocation.agencyId();
        e.resourceId = allocation.resourceId();
        e.resourceType = allocation.resourceType().name();
        e.assignedToUserId = allocation.assignedToUserId();
        e.missionId = allocation.missionId();
        e.status = allocation.status().name();
        e.allocatedAt = allocation.allocatedAt();
        e.releasedAt = allocation.releasedAt();
        e.createdAt = allocation.createdAt();
        e.updatedAt = allocation.updatedAt();
        return e;
    }

    public ResourceAllocation toDomain() {
        return ResourceAllocation.rehydrate(id, tenantId, agencyId, resourceId,
                ResourceType.valueOf(resourceType), assignedToUserId, missionId,
                AllocationStatus.valueOf(status), allocatedAt, releasedAt, createdAt, updatedAt);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public UUID getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(UUID assignedToUserId) { this.assignedToUserId = assignedToUserId; }
    public UUID getMissionId() { return missionId; }
    public void setMissionId(UUID missionId) { this.missionId = missionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(Instant allocatedAt) { this.allocatedAt = allocatedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
