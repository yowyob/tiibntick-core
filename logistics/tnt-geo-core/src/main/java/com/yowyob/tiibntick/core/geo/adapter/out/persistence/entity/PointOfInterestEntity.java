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
 * R2DBC persistence entity for tnt_geography.points_of_interest.
 *
 * Author: MANFOUO Braun
 */
@Table(schema = "tnt_geography", value = "points_of_interest")
public class PointOfInterestEntity implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("name")
    private String name;

    @Column("type")
    private String type;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("description")
    private String description;

    @Column("city_code")
    private String cityCode;

    @Column("is_verified")
    private boolean verified;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public PointOfInterestEntity() {}

    public static PointOfInterestEntity fromDomain(PointOfInterest poi) {
        PointOfInterestEntity e = new PointOfInterestEntity();
        e.id = poi.id();
        e.tenantId = poi.tenantId();
        e.name = poi.name();
        e.type = poi.type().name();
        e.latitude = poi.coordinates().latitude();
        e.longitude = poi.coordinates().longitude();
        e.description = poi.description();
        e.cityCode = poi.cityCode();
        e.verified = poi.isVerified();
        e.createdAt = poi.createdAt();
        e.updatedAt = poi.updatedAt();
        return e;
    }

    public PointOfInterest toDomain() {
        return PointOfInterest.rehydrate(
                id, tenantId, name,
                PoiType.valueOf(type),
                GeoPoint.of(latitude, longitude),
                description, cityCode, verified, createdAt, updatedAt
        );
    }

    @Override public boolean isNew()     { return isNew; }
    public void setNew(boolean isNew)   { this.isNew = isNew; }

    public UUID getId()                 { return id; }
    public void setId(UUID id)          { this.id = id; }
    public UUID getTenantId()           { return tenantId; }
    public void setTenantId(UUID t)     { this.tenantId = t; }
    public String getName()             { return name; }
    public void setName(String n)       { this.name = n; }
    public String getType()             { return type; }
    public void setType(String t)       { this.type = t; }
    public Double getLatitude()         { return latitude; }
    public void setLatitude(Double l)   { this.latitude = l; }
    public Double getLongitude()        { return longitude; }
    public void setLongitude(Double l)  { this.longitude = l; }
    public String getDescription()      { return description; }
    public void setDescription(String d){ this.description = d; }
    public String getCityCode()         { return cityCode; }
    public void setCityCode(String c)   { this.cityCode = c; }
    public boolean isVerified()         { return verified; }
    public void setVerified(boolean v)  { this.verified = v; }
    public Instant getCreatedAt()       { return createdAt; }
    public void setCreatedAt(Instant t) { this.createdAt = t; }
    public Instant getUpdatedAt()       { return updatedAt; }
    public void setUpdatedAt(Instant t) { this.updatedAt = t; }
}
