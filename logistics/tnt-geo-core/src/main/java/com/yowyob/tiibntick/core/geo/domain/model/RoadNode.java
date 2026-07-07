package com.yowyob.tiibntick.core.geo.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing a node in the TiiBnTick road network graph.
 * Nodes are the vertices of the directed weighted graph used by tnt-route-core's A* algorithm.
 *
 * A node is tenant-scoped: each logistics tenant owns its own road network.
 *
 * Author: MANFOUO Braun
 */
public final class RoadNode {

    private final RoadNodeId id;
    private final UUID tenantId;
    private final NodeType type;
    private GeoPoint coordinates;
    private String name;
    private String cityCode;
    private boolean active;
    private Integer capacitySlots;
    private final Instant createdAt;
    private Instant updatedAt;

    private RoadNode(RoadNodeId id, UUID tenantId, NodeType type, GeoPoint coordinates,
                     String name, String cityCode, boolean active, Integer capacitySlots,
                     Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates must not be null");
        this.name = requireText(name, "name");
        this.cityCode = requireText(cityCode, "cityCode").toUpperCase();
        this.active = active;
        this.capacitySlots = capacitySlots;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static RoadNode create(UUID tenantId, NodeType type, GeoPoint coordinates,
                                  String name, String cityCode, Integer capacitySlots) {
        Instant now = Instant.now();
        return new RoadNode(
                RoadNodeId.generate(), tenantId, type, coordinates,
                name, cityCode, true, capacitySlots, now, now
        );
    }

    public static RoadNode rehydrate(RoadNodeId id, UUID tenantId, NodeType type,
                                     GeoPoint coordinates, String name, String cityCode,
                                     boolean active, Integer capacitySlots,
                                     Instant createdAt, Instant updatedAt) {
        return new RoadNode(id, tenantId, type, coordinates, name, cityCode, active,
                capacitySlots, createdAt, updatedAt);
    }

    public void updateCoordinates(GeoPoint newCoordinates) {
        this.coordinates = Objects.requireNonNull(newCoordinates, "coordinates must not be null");
        this.updatedAt = Instant.now();
    }

    public void rename(String newName) {
        this.name = requireText(newName, "name");
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void updateCapacity(Integer slots) {
        this.capacitySlots = slots;
        this.updatedAt = Instant.now();
    }

    public RoadNodeId id()            { return id; }
    public UUID tenantId()            { return tenantId; }
    public NodeType type()            { return type; }
    public GeoPoint coordinates()     { return coordinates; }
    public String name()              { return name; }
    public String cityCode()          { return cityCode; }
    public boolean isActive()         { return active; }
    public Integer capacitySlots()    { return capacitySlots; }
    public Instant createdAt()        { return createdAt; }
    public Instant updatedAt()        { return updatedAt; }

    /**
     * Haversine distance to another node in kilometres.
     */
    public double distanceTo(RoadNode other) {
        return this.coordinates.haversineDistanceTo(other.coordinates);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoadNode that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RoadNode{id=" + id + ", name='" + name + "', type=" + type + ", active=" + active + "}";
    }
}
