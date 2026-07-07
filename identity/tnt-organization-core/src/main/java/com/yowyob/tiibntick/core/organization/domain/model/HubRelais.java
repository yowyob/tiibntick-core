package com.yowyob.tiibntick.core.organization.domain.model;

import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate Root representing a TiiBnTick physical relay point (Hub Relais).
 *
 * <p>A HubRelais is a geolocated physical location where parcels can be dropped off
 * or picked up. It is the cornerstone of TiiBnTick's informal logistics network
 * (e.g., market stalls, local shops, community centres acting as relay points).
 *
 * <p>Geographic location is stored as a PostGIS-compatible WKT POINT string
 * (SRID 4326 — WGS 84). This enables native PostGIS queries such as:
 * <pre>{@code
 *     ST_Within(ST_GeomFromText(geographic_point_wkt, 4326), ST_GeomFromText(:polygon, 4326))
 * }</pre>
 *
 * <p>Kernel integration:
 * <ul>
 *   <li>{@code organizationId} — UUID of the Kernel Organization that owns or operates
 *       this relay hub. Validated via
 *       {@link com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort}
 *       before creation. Must not be null.</li>
 * </ul>
 *
 * <p>No Java inheritance from any RT-comops Kernel class.
 *
 * @author MANFOUO Braun
 */
public class HubRelais {

    /** TiiBnTick internal relay hub identifier. */
    private final OrganizationId id;

    /**
     * Kernel integration key.
     * References the Organization entity in RT-comops-organization-core.
     * Must not be null.
     */
    private final UUID organizationId;

    /** Multi-tenant isolation key. */
    private final UUID tenantId;

    /** Display name of the relay hub (e.g., "Marché Central Douala Point Relais"). */
    private String name;

    /**
     * Maximum parcel storage capacity.
     * Business constraint: must be strictly positive.
     */
    private int maxParcelCapacity;

    /**
     * Geographic position as WKT POINT string (SRID 4326).
     * Format: {@code POINT(longitude latitude)}
     * Example: {@code POINT(9.7022 4.0511)} for Douala.
     */
    private String geographicPointWkt;

    /** Opening hours in free-text format (e.g., "Mon-Sat 08:00-18:00"). */
    private String openingHours;

    /**
     * Operator actor ID — references a TiiBnTick actor (tnt-actor-core).
     * The person or entity responsible for managing this relay point.
     * Nullable: some hubs may be unassigned at creation time.
     */
    private UUID operatorId;

    /** Whether this hub is currently accepting parcels. */
    private boolean operational;

    /** Record creation timestamp (UTC). */
    private final Instant createdAt;

    /** Last modification timestamp (UTC). */
    private Instant updatedAt;

    /**
     * Full constructor — used by repository adapters when reconstituting from persistence.
     *
     * @param id                  TiiBnTick internal relay hub ID
     * @param organizationId      Kernel organization UUID (must not be null)
     * @param tenantId            Multi-tenant key
     * @param name                Relay hub name
     * @param maxParcelCapacity   Maximum parcel capacity (must be &gt; 0)
     * @param geographicPointWkt  PostGIS WKT POINT string (SRID 4326)
     * @param openingHours        Free-text opening hours
     * @param operatorId          Operator actor UUID (nullable)
     * @param operational         Operational status
     * @param createdAt           Creation timestamp
     * @param updatedAt           Last update timestamp
     */
    public HubRelais(OrganizationId id,
                     UUID organizationId,
                     UUID tenantId,
                     String name,
                     int maxParcelCapacity,
                     String geographicPointWkt,
                     String openingHours,
                     UUID operatorId,
                     boolean operational,
                     Instant createdAt,
                     Instant updatedAt) {
        if (organizationId == null) {
            throw new IllegalArgumentException("HubRelais.organizationId (Kernel key) must not be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("HubRelais.tenantId must not be null");
        }
        if (maxParcelCapacity <= 0) {
            throw new IllegalArgumentException("HubRelais.maxParcelCapacity must be strictly positive");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.tenantId = tenantId;
        this.name = name;
        this.maxParcelCapacity = maxParcelCapacity;
        this.geographicPointWkt = geographicPointWkt;
        this.openingHours = openingHours;
        this.operatorId = operatorId;
        this.operational = operational;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method — creates a new HubRelais with a generated ID and current timestamps.
     *
     * @param organizationId     Kernel organization UUID (validated upstream)
     * @param tenantId           Multi-tenant key
     * @param name               Relay hub name
     * @param maxParcelCapacity  Maximum parcel capacity (must be &gt; 0)
     * @param geographicPointWkt PostGIS WKT POINT string
     * @param openingHours       Free-text opening hours
     * @param operatorId         Operator actor UUID (nullable)
     * @return a new, unsaved {@link HubRelais} instance
     */
    public static HubRelais create(UUID organizationId,
                                   UUID tenantId,
                                   String name,
                                   int maxParcelCapacity,
                                   String geographicPointWkt,
                                   String openingHours,
                                   UUID operatorId) {
        Instant now = Instant.now();
        return new HubRelais(
                OrganizationId.generate(),
                organizationId,
                tenantId,
                name,
                maxParcelCapacity,
                geographicPointWkt,
                openingHours,
                operatorId,
                true,
                now,
                now
        );
    }

    // ─── Business methods ────────────────────────────────────────────────────

    /**
     * Checks whether this relay hub can accept more parcels.
     *
     * @param currentOccupancy the current number of parcels stored at this hub
     * @return {@code true} if {@code currentOccupancy < maxParcelCapacity}
     */
    public boolean hasAvailableCapacity(int currentOccupancy) {
        return currentOccupancy < maxParcelCapacity;
    }

    /**
     * Assigns or reassigns the operator for this relay hub.
     *
     * @param newOperatorId the new operator's UUID from tnt-actor-core
     */
    public void assignOperator(UUID newOperatorId) {
        this.operatorId = newOperatorId;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the relay hub as temporarily out of service.
     */
    public void suspend() {
        this.operational = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the relay hub as operational again.
     */
    public void resume() {
        this.operational = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the maximum parcel capacity.
     *
     * @param newCapacity the new capacity (must be strictly positive)
     */
    public void updateCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be strictly positive");
        }
        this.maxParcelCapacity = newCapacity;
        this.updatedAt = Instant.now();
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    public OrganizationId getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public int getMaxParcelCapacity() { return maxParcelCapacity; }
    public String getGeographicPointWkt() { return geographicPointWkt; }
    public String getOpeningHours() { return openingHours; }
    public UUID getOperatorId() { return operatorId; }
    public boolean isOperational() { return operational; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
