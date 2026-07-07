package com.yowyob.tiibntick.core.geo.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the tnt_geography.road_arcs table.
 *
 * Author: MANFOUO Braun
 */
@Table(schema = "tnt_geography", value = "road_arcs")
public class RoadArcEntity {

    @Id
    @Column("id")
    private String id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("source_id")
    private String sourceId;

    @Column("target_id")
    private String targetId;

    @Column("distance_km")
    private Double distanceKm;

    @Column("road_type")
    private String roadType;

    @Column("base_speed_kmh")
    private Double baseSpeedKmh;

    @Column("traffic_factor")
    private Double trafficFactor;

    @Column("is_bidirectional")
    private boolean bidirectional;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public RoadArcEntity() {}

    public static RoadArcEntity fromDomain(RoadArc arc) {
        RoadArcEntity e = new RoadArcEntity();
        e.id = arc.id().value();
        e.tenantId = arc.tenantId();
        e.sourceId = arc.sourceId().value();
        e.targetId = arc.targetId().value();
        e.distanceKm = arc.distanceKm();
        e.roadType = arc.roadType().name();
        e.baseSpeedKmh = arc.baseSpeedKmh();
        e.trafficFactor = arc.trafficFactor();
        e.bidirectional = arc.isBidirectional();
        e.createdAt = arc.createdAt();
        e.updatedAt = arc.updatedAt();
        return e;
    }

    public RoadArc toDomain() {
        return RoadArc.rehydrate(
                RoadArcId.of(id),
                tenantId,
                RoadNodeId.of(sourceId),
                RoadNodeId.of(targetId),
                distanceKm,
                RoadType.valueOf(roadType),
                baseSpeedKmh,
                trafficFactor,
                bidirectional,
                createdAt,
                updatedAt
        );
    }

    public String getId()               { return id; }
    public void setId(String id)        { this.id = id; }
    public UUID getTenantId()           { return tenantId; }
    public void setTenantId(UUID t)     { this.tenantId = t; }
    public String getSourceId()         { return sourceId; }
    public void setSourceId(String s)   { this.sourceId = s; }
    public String getTargetId()         { return targetId; }
    public void setTargetId(String t)   { this.targetId = t; }
    public Double getDistanceKm()       { return distanceKm; }
    public void setDistanceKm(Double d) { this.distanceKm = d; }
    public String getRoadType()         { return roadType; }
    public void setRoadType(String r)   { this.roadType = r; }
    public Double getBaseSpeedKmh()     { return baseSpeedKmh; }
    public void setBaseSpeedKmh(Double s){ this.baseSpeedKmh = s; }
    public Double getTrafficFactor()    { return trafficFactor; }
    public void setTrafficFactor(Double f){ this.trafficFactor = f; }
    public boolean isBidirectional()    { return bidirectional; }
    public void setBidirectional(boolean b){ this.bidirectional = b; }
    public Instant getCreatedAt()       { return createdAt; }
    public void setCreatedAt(Instant t) { this.createdAt = t; }
    public Instant getUpdatedAt()       { return updatedAt; }
    public void setUpdatedAt(Instant t) { this.updatedAt = t; }
}
