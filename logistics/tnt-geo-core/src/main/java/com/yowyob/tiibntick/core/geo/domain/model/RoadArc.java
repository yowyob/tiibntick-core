package com.yowyob.tiibntick.core.geo.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A directed, weighted edge in the TiiBnTick road network graph.
 * Connects two {@link RoadNode} instances and carries all parameters
 * required to compute the 5D cost omega(a,t).
 *
 * If {@code bidirectional} is true, the edge is logically duplicated
 * in reverse during graph construction in {@link RoadNetwork}.
 *
 * Author: MANFOUO Braun
 */
public final class RoadArc {

    private static final double DEFAULT_TRAFFIC_FACTOR = 1.0;
    private static final double DEFAULT_BASE_SPEED_PAVED_KMH = 50.0;
    private static final double DEFAULT_BASE_SPEED_DIRT_KMH = 20.0;

    private final RoadArcId id;
    private final UUID tenantId;
    private final RoadNodeId sourceId;
    private final RoadNodeId targetId;
    private final double distanceKm;
    private final RoadType roadType;
    private double baseSpeedKmh;
    private double trafficFactor;
    private final boolean bidirectional;
    private final Instant createdAt;
    private Instant updatedAt;

    private RoadArc(RoadArcId id, UUID tenantId, RoadNodeId sourceId, RoadNodeId targetId,
                    double distanceKm, RoadType roadType, double baseSpeedKmh,
                    double trafficFactor, boolean bidirectional,
                    Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
        if (distanceKm <= 0) throw new IllegalArgumentException("distanceKm must be > 0");
        if (baseSpeedKmh <= 0) throw new IllegalArgumentException("baseSpeedKmh must be > 0");
        if (trafficFactor <= 0) throw new IllegalArgumentException("trafficFactor must be > 0");
        this.distanceKm = distanceKm;
        this.roadType = Objects.requireNonNull(roadType, "roadType must not be null");
        this.baseSpeedKmh = baseSpeedKmh;
        this.trafficFactor = trafficFactor;
        this.bidirectional = bidirectional;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static RoadArc create(UUID tenantId, RoadNodeId sourceId, RoadNodeId targetId,
                                 double distanceKm, RoadType roadType, boolean bidirectional) {
        Instant now = Instant.now();
        double speed = (roadType == RoadType.DIRT) ? DEFAULT_BASE_SPEED_DIRT_KMH : DEFAULT_BASE_SPEED_PAVED_KMH;
        return new RoadArc(
                RoadArcId.generate(), tenantId, sourceId, targetId, distanceKm, roadType,
                speed, DEFAULT_TRAFFIC_FACTOR, bidirectional, now, now
        );
    }

    public static RoadArc createWithSpeed(UUID tenantId, RoadNodeId sourceId, RoadNodeId targetId,
                                          double distanceKm, RoadType roadType, double baseSpeedKmh,
                                          boolean bidirectional) {
        Instant now = Instant.now();
        return new RoadArc(
                RoadArcId.generate(), tenantId, sourceId, targetId, distanceKm, roadType,
                baseSpeedKmh, DEFAULT_TRAFFIC_FACTOR, bidirectional, now, now
        );
    }

    public static RoadArc rehydrate(RoadArcId id, UUID tenantId, RoadNodeId sourceId, RoadNodeId targetId,
                                    double distanceKm, RoadType roadType, double baseSpeedKmh,
                                    double trafficFactor, boolean bidirectional,
                                    Instant createdAt, Instant updatedAt) {
        return new RoadArc(id, tenantId, sourceId, targetId, distanceKm, roadType,
                baseSpeedKmh, trafficFactor, bidirectional, createdAt, updatedAt);
    }

    /**
     * Updates the real-time traffic congestion factor.
     * trafficFactor = 1.0 means free-flow; > 1.0 means congestion.
     */
    public void updateTrafficFactor(double newFactor) {
        if (newFactor <= 0) throw new IllegalArgumentException("trafficFactor must be > 0");
        this.trafficFactor = newFactor;
        this.updatedAt = Instant.now();
    }

    public void updateBaseSpeed(double newSpeedKmh) {
        if (newSpeedKmh <= 0) throw new IllegalArgumentException("baseSpeedKmh must be > 0");
        this.baseSpeedKmh = newSpeedKmh;
        this.updatedAt = Instant.now();
    }

    /**
     * Effective travel speed considering congestion: effectiveSpeed = baseSpeed / trafficFactor.
     */
    public double effectiveSpeedKmh() {
        return baseSpeedKmh / trafficFactor;
    }

    /**
     * Travel time in hours under current traffic conditions.
     */
    public double travelTimeHours() {
        return distanceKm / effectiveSpeedKmh();
    }

    /**
     * Returns the penibility coefficient rho from the road type (used in the 5D cost function).
     */
    public double penibility() {
        return roadType.penibility();
    }

    public RoadArcId id()           { return id; }
    public UUID tenantId()          { return tenantId; }
    public RoadNodeId sourceId()    { return sourceId; }
    public RoadNodeId targetId()    { return targetId; }
    public double distanceKm()      { return distanceKm; }
    public RoadType roadType()      { return roadType; }
    public double baseSpeedKmh()    { return baseSpeedKmh; }
    public double trafficFactor()   { return trafficFactor; }
    public boolean isBidirectional(){ return bidirectional; }
    public Instant createdAt()      { return createdAt; }
    public Instant updatedAt()      { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoadArc that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RoadArc{id=" + id + ", " + sourceId + " -> " + targetId
                + ", dist=" + distanceKm + "km, type=" + roadType + "}";
    }
}
