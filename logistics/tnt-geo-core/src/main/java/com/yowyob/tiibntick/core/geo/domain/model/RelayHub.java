package com.yowyob.tiibntick.core.geo.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A physical parcel relay hub managed by an agency branch.
 * RelayHubs are RELAY_HUB-typed road network nodes that can temporarily store parcels.
 *
 * Author: MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class RelayHub {

    private final UUID id;
    private final UUID tenantId;
    private final UUID branchId;
    private final RoadNodeId nodeId;
    private final int capacitySlots;
    private int currentOccupancy;
    private final String operatorActorId;
    private HubStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private RelayHub(UUID id, UUID tenantId, UUID branchId, RoadNodeId nodeId,
                     int capacitySlots, int currentOccupancy, String operatorActorId,
                     HubStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.branchId = Objects.requireNonNull(branchId, "branchId must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (capacitySlots <= 0) throw new IllegalArgumentException("capacitySlots must be > 0");
        if (currentOccupancy < 0) throw new IllegalArgumentException("currentOccupancy must be >= 0");
        if (currentOccupancy > capacitySlots) {
            throw new IllegalArgumentException("currentOccupancy cannot exceed capacitySlots");
        }
        this.capacitySlots = capacitySlots;
        this.currentOccupancy = currentOccupancy;
        this.operatorActorId = operatorActorId;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static RelayHub create(UUID tenantId, UUID branchId, RoadNodeId nodeId,
                                  int capacitySlots, String operatorActorId) {
        Instant now = Instant.now();
        return new RelayHub(UUID.randomUUID(), tenantId, branchId, nodeId, capacitySlots, 0,
                operatorActorId, HubStatus.ACTIVE, now, now);
    }

    public static RelayHub rehydrate(UUID id, UUID tenantId, UUID branchId, RoadNodeId nodeId,
                                     int capacitySlots, int currentOccupancy, String operatorActorId,
                                     HubStatus status, Instant createdAt, Instant updatedAt) {
        return new RelayHub(id, tenantId, branchId, nodeId, capacitySlots, currentOccupancy,
                operatorActorId, status, createdAt, updatedAt);
    }

    public void addParcel() {
        if (currentOccupancy >= capacitySlots) {
            throw new IllegalStateException("Hub " + id + " is at full capacity");
        }
        this.currentOccupancy++;
        if (this.currentOccupancy == this.capacitySlots) {
            this.status = HubStatus.FULL;
        }
        this.updatedAt = Instant.now();
    }

    public void removeParcel() {
        if (currentOccupancy <= 0) {
            throw new IllegalStateException("Hub " + id + " is already empty");
        }
        this.currentOccupancy--;
        if (this.status == HubStatus.FULL && this.currentOccupancy < this.capacitySlots) {
            this.status = HubStatus.ACTIVE;
        }
        this.updatedAt = Instant.now();
    }

    public void updateOccupancy(int occupancy) {
        if (occupancy < 0 || occupancy > capacitySlots) {
            throw new IllegalArgumentException("occupancy " + occupancy + " is out of range [0, " + capacitySlots + "]");
        }
        this.currentOccupancy = occupancy;
        this.status = (occupancy == capacitySlots) ? HubStatus.FULL : HubStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void close()          { this.status = HubStatus.TEMPORARILY_CLOSED; this.updatedAt = Instant.now(); }
    public void reopen()         { this.status = HubStatus.ACTIVE; this.updatedAt = Instant.now(); }
    public void permanentlyClose(){ this.status = HubStatus.PERMANENTLY_CLOSED; this.updatedAt = Instant.now(); }

    public boolean isAvailable() { return status == HubStatus.ACTIVE; }
    public int availableSlots()  { return capacitySlots - currentOccupancy; }
    public double occupancyRate(){ return (double) currentOccupancy / capacitySlots; }

    public UUID id()                  { return id; }
    public UUID tenantId()            { return tenantId; }
    public UUID branchId()            { return branchId; }
    public RoadNodeId nodeId()        { return nodeId; }
    public int capacitySlots()        { return capacitySlots; }
    public int currentOccupancy()     { return currentOccupancy; }
    public String operatorActorId()   { return operatorActorId; }
    public HubStatus status()         { return status; }
    public Instant createdAt()        { return createdAt; }
    public Instant updatedAt()        { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelayHub that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "RelayHub{id=" + id + ", node=" + nodeId + ", occupancy="
                + currentOccupancy + "/" + capacitySlots + ", status=" + status + "}";
    }
}
