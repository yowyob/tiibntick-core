package com.yowyob.tiibntick.core.geo.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for tnt_geography.relay_hubs.
 *
 * Author: MANFOUO Braun
 */
@Table(schema = "tnt_geography", value = "relay_hubs")
public class RelayHubEntity implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("branch_id")
    private UUID branchId;

    @Column("node_id")
    private String nodeId;

    @Column("capacity_slots")
    private int capacitySlots;

    @Column("current_occupancy")
    private int currentOccupancy;

    @Column("operator_actor_id")
    private String operatorActorId;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public RelayHubEntity() {}

    public static RelayHubEntity fromDomain(RelayHub hub) {
        RelayHubEntity e = new RelayHubEntity();
        e.id = hub.id();
        e.tenantId = hub.tenantId();
        e.branchId = hub.branchId();
        e.nodeId = hub.nodeId().value();
        e.capacitySlots = hub.capacitySlots();
        e.currentOccupancy = hub.currentOccupancy();
        e.operatorActorId = hub.operatorActorId();
        e.status = hub.status().name();
        e.createdAt = hub.createdAt();
        e.updatedAt = hub.updatedAt();
        return e;
    }

    public RelayHub toDomain() {
        return RelayHub.rehydrate(
                id, tenantId, branchId,
                RoadNodeId.of(nodeId),
                capacitySlots, currentOccupancy,
                operatorActorId,
                HubStatus.valueOf(status),
                createdAt, updatedAt
        );
    }

    @Override public boolean isNew()         { return isNew; }
    public void setNew(boolean isNew)       { this.isNew = isNew; }

    public UUID getId()                     { return id; }
    public void setId(UUID id)              { this.id = id; }
    public UUID getTenantId()               { return tenantId; }
    public void setTenantId(UUID t)         { this.tenantId = t; }
    public UUID getBranchId()               { return branchId; }
    public void setBranchId(UUID b)         { this.branchId = b; }
    public String getNodeId()               { return nodeId; }
    public void setNodeId(String n)         { this.nodeId = n; }
    public int getCapacitySlots()           { return capacitySlots; }
    public void setCapacitySlots(int c)     { this.capacitySlots = c; }
    public int getCurrentOccupancy()        { return currentOccupancy; }
    public void setCurrentOccupancy(int o)  { this.currentOccupancy = o; }
    public String getOperatorActorId()      { return operatorActorId; }
    public void setOperatorActorId(String o){ this.operatorActorId = o; }
    public String getStatus()               { return status; }
    public void setStatus(String s)         { this.status = s; }
    public Instant getCreatedAt()           { return createdAt; }
    public void setCreatedAt(Instant t)     { this.createdAt = t; }
    public Instant getUpdatedAt()           { return updatedAt; }
    public void setUpdatedAt(Instant t)     { this.updatedAt = t; }
}
