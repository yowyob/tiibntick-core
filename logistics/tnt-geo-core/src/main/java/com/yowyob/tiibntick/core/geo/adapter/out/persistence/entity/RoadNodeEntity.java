package com.yowyob.tiibntick.core.geo.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the tnt_geography.road_nodes table.
 * Stores coordinates as separate lat/lng columns; PostGIS geometry is managed
 * via native SQL queries using ST_MakePoint(longitude, latitude).
 *
 * Author: MANFOUO Braun
 */
@Table(schema = "tnt_geography", value = "road_nodes")
public class RoadNodeEntity {

    @Id
    @Column("id")
    private String id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("type")
    private String type;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("name")
    private String name;

    @Column("city_code")
    private String cityCode;

    @Column("is_active")
    private boolean active;

    @Column("capacity_slots")
    private Integer capacitySlots;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public RoadNodeEntity() {}

    public static RoadNodeEntity fromDomain(RoadNode node) {
        RoadNodeEntity e = new RoadNodeEntity();
        e.id = node.id().value();
        e.tenantId = node.tenantId();
        e.type = node.type().name();
        e.latitude = node.coordinates().latitude();
        e.longitude = node.coordinates().longitude();
        e.name = node.name();
        e.cityCode = node.cityCode();
        e.active = node.isActive();
        e.capacitySlots = node.capacitySlots();
        e.createdAt = node.createdAt();
        e.updatedAt = node.updatedAt();
        return e;
    }

    public RoadNode toDomain() {
        return RoadNode.rehydrate(
                RoadNodeId.of(id),
                tenantId,
                NodeType.valueOf(type),
                GeoPoint.of(latitude, longitude),
                name,
                cityCode,
                active,
                capacitySlots,
                createdAt,
                updatedAt
        );
    }

    public String getId()           { return id; }
    public void setId(String id)    { this.id = id; }
    public UUID getTenantId()       { return tenantId; }
    public void setTenantId(UUID t) { this.tenantId = t; }
    public String getType()         { return type; }
    public void setType(String t)   { this.type = t; }
    public Double getLatitude()     { return latitude; }
    public void setLatitude(Double l){ this.latitude = l; }
    public Double getLongitude()    { return longitude; }
    public void setLongitude(Double l){ this.longitude = l; }
    public String getName()         { return name; }
    public void setName(String n)   { this.name = n; }
    public String getCityCode()     { return cityCode; }
    public void setCityCode(String c){ this.cityCode = c; }
    public boolean isActive()       { return active; }
    public void setActive(boolean a){ this.active = a; }
    public Integer getCapacitySlots()      { return capacitySlots; }
    public void setCapacitySlots(Integer c){ this.capacitySlots = c; }
    public Instant getCreatedAt()          { return createdAt; }
    public void setCreatedAt(Instant t)    { this.createdAt = t; }
    public Instant getUpdatedAt()          { return updatedAt; }
    public void setUpdatedAt(Instant t)    { this.updatedAt = t; }
}
