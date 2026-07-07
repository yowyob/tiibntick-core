package com.yowyob.tiibntick.core.geo.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A local landmark or facility relevant to TiiBnTick logistics operations.
 * POIs serve as African-context geocoding fallback references and routing hints.
 *
 * Author: MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class PointOfInterest {

    private final UUID id;
    private final UUID tenantId;
    private final String name;
    private final PoiType type;
    private GeoPoint coordinates;
    private String description;
    private final String cityCode;
    private boolean verified;
    private final Instant createdAt;
    private Instant updatedAt;

    private PointOfInterest(UUID id, UUID tenantId, String name, PoiType type,
                            GeoPoint coordinates, String description, String cityCode,
                            boolean verified, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.name = requireText(name, "name");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates must not be null");
        this.description = description;
        this.cityCode = requireText(cityCode, "cityCode").toUpperCase();
        this.verified = verified;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static PointOfInterest create(UUID tenantId, String name, PoiType type,
                                         GeoPoint coordinates, String description, String cityCode) {
        Instant now = Instant.now();
        return new PointOfInterest(UUID.randomUUID(), tenantId, name, type, coordinates,
                description, cityCode, false, now, now);
    }

    public static PointOfInterest rehydrate(UUID id, UUID tenantId, String name, PoiType type,
                                            GeoPoint coordinates, String description, String cityCode,
                                            boolean verified, Instant createdAt, Instant updatedAt) {
        return new PointOfInterest(id, tenantId, name, type, coordinates, description,
                cityCode, verified, createdAt, updatedAt);
    }

    public void verify() {
        this.verified = true;
        this.updatedAt = Instant.now();
    }

    public void updateCoordinates(GeoPoint newCoords) {
        this.coordinates = Objects.requireNonNull(newCoords);
        this.updatedAt = Instant.now();
    }

    public void updateDescription(String desc) {
        this.description = desc;
        this.updatedAt = Instant.now();
    }

    public UUID id()                { return id; }
    public UUID tenantId()          { return tenantId; }
    public String name()            { return name; }
    public PoiType type()           { return type; }
    public GeoPoint coordinates()   { return coordinates; }
    public String description()     { return description; }
    public String cityCode()        { return cityCode; }
    public boolean isVerified()     { return verified; }
    public Instant createdAt()      { return createdAt; }
    public Instant updatedAt()      { return updatedAt; }

    private static String requireText(String v, String field) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return v.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PointOfInterest that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "POI{id=" + id + ", name='" + name + "', type=" + type + ", city=" + cityCode + "}";
    }
}
