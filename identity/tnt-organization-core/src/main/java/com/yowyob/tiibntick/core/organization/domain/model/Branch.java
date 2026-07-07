package com.yowyob.tiibntick.core.organization.domain.model;

import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate Root representing a TiiBnTick Branch (sub-office of an Agency).
 *
 * <p>A Branch is a physical or operational subdivision of an {@link Agency}.
 * Examples: "TiiBnTick Douala — Akwa Branch", "TiiBnTick Yaoundé — Bastos Branch".
 *
 * <p>Kernel integration:
 * <ul>
 *   <li>{@code organizationId} — UUID of the Kernel Organization entity associated
 *       with this branch. Validated via
 *       {@link com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort}
 *       before creation.</li>
 * </ul>
 *
 * <p>No Java inheritance from any RT-comops Kernel class.
 *
 * @author MANFOUO Braun
 */
public class Branch {

    /** TiiBnTick internal branch identifier. */
    private final OrganizationId id;

    /**
     * Kernel integration key.
     * References the Organization in RT-comops-organization-core.
     * Must not be null.
     */
    private final UUID organizationId;

    /** Internal ID of the parent {@link Agency}. */
    private final OrganizationId agencyId;

    /** Multi-tenant isolation key. */
    private final UUID tenantId;

    /** Branch display name. */
    private String name;

    /** Physical address (free-form text — may be informal in Cameroonian context). */
    private String address;

    /** Optional geographic coverage zone for this branch. */
    private ServiceZone serviceZone;

    /** Whether this branch is currently operational. */
    private boolean active;

    /** Record creation timestamp (UTC). */
    private final Instant createdAt;

    /** Last modification timestamp (UTC). */
    private Instant updatedAt;

    /**
     * Full constructor — used by repository adapters when reconstituting from persistence.
     *
     * @param id             TiiBnTick internal branch ID
     * @param organizationId Kernel organization UUID (must not be null)
     * @param agencyId       Parent agency ID (must not be null)
     * @param tenantId       Multi-tenant key
     * @param name           Branch name
     * @param address        Physical address
     * @param serviceZone    Optional coverage zone
     * @param active         Operational status
     * @param createdAt      Creation timestamp
     * @param updatedAt      Last update timestamp
     */
    public Branch(OrganizationId id,
                  UUID organizationId,
                  OrganizationId agencyId,
                  UUID tenantId,
                  String name,
                  String address,
                  ServiceZone serviceZone,
                  boolean active,
                  Instant createdAt,
                  Instant updatedAt) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Branch.organizationId (Kernel key) must not be null");
        }
        if (agencyId == null) {
            throw new IllegalArgumentException("Branch.agencyId must not be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Branch.tenantId must not be null");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.agencyId = agencyId;
        this.tenantId = tenantId;
        this.name = name;
        this.address = address;
        this.serviceZone = serviceZone;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method — creates a new Branch with a generated ID and current timestamps.
     *
     * @param organizationId Kernel organization UUID (validated upstream)
     * @param agencyId       Parent agency ID
     * @param tenantId       Multi-tenant key
     * @param name           Branch name
     * @param address        Physical address
     * @param serviceZone    Optional coverage zone (may be null)
     * @return a new, unsaved {@link Branch} instance
     */
    public static Branch create(UUID organizationId,
                                OrganizationId agencyId,
                                UUID tenantId,
                                String name,
                                String address,
                                ServiceZone serviceZone) {
        Instant now = Instant.now();
        return new Branch(
                OrganizationId.generate(),
                organizationId,
                agencyId,
                tenantId,
                name,
                address,
                serviceZone,
                true,
                now,
                now
        );
    }

    // ─── Business methods ────────────────────────────────────────────────────

    /**
     * Deactivates the branch (e.g., temporary closure).
     */
    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Reactivates a previously deactivated branch.
     */
    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the branch address.
     *
     * @param newAddress the new physical address
     */
    public void relocate(String newAddress) {
        this.address = newAddress;
        this.updatedAt = Instant.now();
    }

    /**
     * Assigns or replaces the service coverage zone.
     *
     * @param zone the new {@link ServiceZone}; may be null to remove coverage
     */
    public void assignServiceZone(ServiceZone zone) {
        this.serviceZone = zone;
        this.updatedAt = Instant.now();
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    public OrganizationId getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public OrganizationId getAgencyId() { return agencyId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public ServiceZone getServiceZone() { return serviceZone; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
