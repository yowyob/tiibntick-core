package com.yowyob.tiibntick.core.realtime.domain.model;

import com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceZoneType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a monitored geofence zone.
 * When a deliverer enters or exits a zone, a {@link GeofenceTrigger} is emitted.
 *
 * <p>Zones are stored in Redis per tenant for fast lookup during GPS ping processing.</p>
 *
 * @author MANFOUO Braun
 */
@Getter
public class GeofenceZone {

    private final String id;
    private final String tenantId;
    private final String name;
    private final GeoCoordinates center;
    private final double radiusMeters;
    private final GeofenceZoneType type;
    private final String linkedEntityId;
    private boolean active;
    private final LocalDateTime createdAt;

    private GeofenceZone(Builder builder) {
        this.id = builder.id;
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.center = Objects.requireNonNull(builder.center, "center must not be null");
        this.radiusMeters = builder.radiusMeters;
        this.type = Objects.requireNonNull(builder.type, "type must not be null");
        this.linkedEntityId = builder.linkedEntityId;
        this.active = true;
        this.createdAt = LocalDateTime.now();

        if (radiusMeters <= 0 || radiusMeters > 50_000) {
            throw new IllegalArgumentException("Radius must be between 1m and 50km");
        }
    }

    /**
     * Determines whether the given coordinates fall within this geofence zone.
     * Uses simple great-circle distance comparison.
     *
     * @param coords the coordinates to test
     * @return true if the coordinates are within the zone radius
     */
    public boolean contains(GeoCoordinates coords) {
        Objects.requireNonNull(coords, "coords must not be null");
        double distanceKm = center.distanceKmTo(coords);
        return distanceKm * 1000 <= radiusMeters;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String tenantId;
        private String name;
        private GeoCoordinates center;
        private double radiusMeters = 500.0;
        private GeofenceZoneType type = GeofenceZoneType.RELAY_HUB;
        private String linkedEntityId;

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder center(GeoCoordinates center) { this.center = center; return this; }
        public Builder radiusMeters(double radiusMeters) { this.radiusMeters = radiusMeters; return this; }
        public Builder type(GeofenceZoneType type) { this.type = type; return this; }
        public Builder linkedEntityId(String linkedEntityId) { this.linkedEntityId = linkedEntityId; return this; }

        public GeofenceZone build() {
            return new GeofenceZone(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeofenceZone z)) return false;
        return Objects.equals(id, z.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GeofenceZone{id=" + id + ", name=" + name + ", radius=" + radiusMeters + "m}";
    }
}
