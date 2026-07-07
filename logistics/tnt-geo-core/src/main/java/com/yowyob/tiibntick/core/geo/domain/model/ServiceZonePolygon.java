package com.yowyob.tiibntick.core.geo.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable geographic polygon defining a service zone for a TiiBnTick agency.
 * Uses the ray-casting algorithm for point-in-polygon containment testing,
 * which is O(n) in the number of polygon vertices.
 *
 * Author: MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class ServiceZonePolygon {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final String name;
    private final List<GeoPoint> vertices;
    private final boolean active;

    // ── : FreelancerOrg owner support ────────────────────────────────────
    /**
     * UUID of the FreelancerOrganization that owns this service zone.
     * Mutually exclusive with {@link #agencyId}: a zone belongs to either an
     * Agency OR a FreelancerOrg. Null for agency-owned zones.
     * References tnt-organization-core UUID — pure integration key (no join).
     */
    private final String freelancerOrgId;

    /**
     * Type of the owner entity: "AGENCY" | "FREELANCER_ORG".
     * Defaults to "AGENCY" for backward compatibility.
     */
    private final String ownerType;

    private ServiceZonePolygon(UUID id, UUID tenantId, UUID agencyId, String name,
                               List<GeoPoint> vertices, boolean active,
                               String freelancerOrgId, String ownerType) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        // agencyId is nullable when ownerType=FREELANCER_ORG
        this.agencyId = agencyId;
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = name.trim();
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("A polygon must have at least 3 vertices");
        }
        this.vertices = Collections.unmodifiableList(new ArrayList<>(vertices));
        this.active = active;
        this.freelancerOrgId = freelancerOrgId;
        this.ownerType = ownerType != null ? ownerType : "AGENCY";
    }

    /** Creates a new agency service zone. */
    public static ServiceZonePolygon create(UUID tenantId, UUID agencyId, String name,
                                            List<GeoPoint> vertices) {
        return new ServiceZonePolygon(UUID.randomUUID(), tenantId, agencyId, name, vertices, true,
                null, "AGENCY");
    }

    /**
     * Creates a new FreelancerOrg service zone ().
     *
     * @param tenantId        tenant scope
     * @param freelancerOrgId the FreelancerOrg UUID (from tnt-organization-core)
     * @param name            zone display name
     * @param vertices        polygon vertices (minimum 3)
     * @return new active ServiceZonePolygon owned by the FreelancerOrg
     */
    public static ServiceZonePolygon createForFreelancerOrg(UUID tenantId, String freelancerOrgId,
                                                              String name, List<GeoPoint> vertices) {
        return new ServiceZonePolygon(UUID.randomUUID(), tenantId, null, name, vertices, true,
                Objects.requireNonNull(freelancerOrgId, "freelancerOrgId must not be null"),
                "FREELANCER_ORG");
    }

    /** Backward-compatible rehydration for agency-owned zones. */
    public static ServiceZonePolygon rehydrate(UUID id, UUID tenantId, UUID agencyId, String name,
                                               List<GeoPoint> vertices, boolean active) {
        return new ServiceZonePolygon(id, tenantId, agencyId, name, vertices, active, null, "AGENCY");
    }

    /**
     * Full rehydration including  FreelancerOrg fields.
     */
    public static ServiceZonePolygon rehydrateFull(UUID id, UUID tenantId, UUID agencyId, String name,
                                                    List<GeoPoint> vertices, boolean active,
                                                    String freelancerOrgId, String ownerType) {
        return new ServiceZonePolygon(id, tenantId, agencyId, name, vertices, active,
                freelancerOrgId, ownerType);
    }

    /**
     * Ray-casting point-in-polygon algorithm.
     * Returns true if the given point falls inside this polygon.
     * Time complexity: O(n) where n = number of vertices.
     *
     * @param point the point to test
     * @return true if point is inside or on the boundary of this polygon
     */
    public boolean contains(GeoPoint point) {
        Objects.requireNonNull(point, "point must not be null");
        int n = vertices.size();
        boolean inside = false;

        double px = point.longitude();
        double py = point.latitude();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = vertices.get(i).longitude();
            double yi = vertices.get(i).latitude();
            double xj = vertices.get(j).longitude();
            double yj = vertices.get(j).latitude();

            boolean intersects = ((yi > py) != (yj > py))
                    && (px < (xj - xi) * (py - yi) / (yj - yi) + xi);
            if (intersects) inside = !inside;
        }
        return inside;
    }

    /**
     * Returns the approximate centroid of the polygon (arithmetic mean of vertices).
     */
    public GeoPoint centroid() {
        double sumLat = 0;
        double sumLng = 0;
        for (GeoPoint v : vertices) {
            sumLat += v.latitude();
            sumLng += v.longitude();
        }
        return GeoPoint.of(sumLat / vertices.size(), sumLng / vertices.size());
    }

    public UUID id()              { return id; }
    public UUID tenantId()        { return tenantId; }
    public UUID agencyId()        { return agencyId; }
    public String name()          { return name; }
    public List<GeoPoint> vertices() { return vertices; }
    public boolean isActive()     { return active; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceZonePolygon that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ServiceZonePolygon{id=" + id + ", name='" + name + "', vertices=" + vertices.size() + "}";
    }
    //  accessors
    public String freelancerOrgId() { return freelancerOrgId; }
    public String ownerType() { return ownerType; }
    public boolean isOwnedByFreelancerOrg() { return "FREELANCER_ORG".equals(ownerType); }

}