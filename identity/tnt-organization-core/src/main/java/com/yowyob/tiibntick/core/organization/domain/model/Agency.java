package com.yowyob.tiibntick.core.organization.domain.model;

import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate Root representing a TiiBnTick Agency (top-level legal entity).
 *
 * <p>An Agency is TiiBnTick's extension of the Kernel Organization concept.
 * It does <strong>NOT</strong> extend any Kernel Java class; instead, it holds
 * {@code organizationId} as a logical UUID key referencing the corresponding
 * {@code RT-comops-organization-core} entity.
 *
 * <p>Business meaning: an Agency is the primary business unit that operates
 * TiiBnTick logistics services in a region (e.g., "TiiBnTick Douala Centre").
 * It can have multiple {@link Branch} offices and multiple {@link HubRelais} relay points.
 *
 * <p>Kernel integration:
 * <ul>
 *   <li>{@code organizationId} — UUID of the Kernel Organization this Agency belongs to.
 *       Validated via {@link com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort}
 *       before creation.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class Agency {

    /** TiiBnTick internal agency identifier. */
    private final OrganizationId id;

    /**
     * Kernel integration key.
     * References the Organization entity in RT-comops-organization-core.
     * Not null — every Agency must be associated with a Kernel organization.
     */
    private final UUID organizationId;

    /** Multi-tenant isolation key. */
    private final UUID tenantId;

    /** Short operating name (e.g., "TiiBnTick Douala"). */
    private String name;

    /** National commerce registry number (e.g., Cameroon RCCM number). */
    private String commerceRegistryNumber;

    /**
     * Primary currency for this agency's transactions.
     * ISO 4217 code — defaults to XAF (CFA Franc BEAC) for Cameroon.
     */
    private String primaryCurrency;

    /** Record creation timestamp (UTC). */
    private final Instant createdAt;

    /** Last modification timestamp (UTC). */
    private Instant updatedAt;

    /**
     * Full constructor — used by repository adapters when reconstituting from persistence.
     *
     * @param id                     TiiBnTick internal agency ID
     * @param organizationId         Kernel organization UUID (must not be null)
     * @param tenantId               Multi-tenant key
     * @param name                   Agency operating name
     * @param commerceRegistryNumber National registry number
     * @param primaryCurrency        ISO 4217 currency code
     * @param createdAt              Creation timestamp
     * @param updatedAt              Last update timestamp
     */
    public Agency(OrganizationId id,
                  UUID organizationId,
                  UUID tenantId,
                  String name,
                  String commerceRegistryNumber,
                  String primaryCurrency,
                  Instant createdAt,
                  Instant updatedAt) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Agency.organizationId (Kernel key) must not be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Agency.tenantId must not be null");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.tenantId = tenantId;
        this.name = name;
        this.commerceRegistryNumber = commerceRegistryNumber;
        this.primaryCurrency = primaryCurrency != null ? primaryCurrency : "XAF";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method — creates a new Agency with a generated ID and current timestamps.
     *
     * @param organizationId         Kernel organization UUID (validated upstream)
     * @param tenantId               Multi-tenant key
     * @param name                   Agency name
     * @param commerceRegistryNumber National registry number
     * @param primaryCurrency        ISO 4217 currency code (null → defaults to "XAF")
     * @return a new, unsaved {@link Agency} instance
     */
    public static Agency create(UUID organizationId,
                                UUID tenantId,
                                String name,
                                String commerceRegistryNumber,
                                String primaryCurrency) {
        Instant now = Instant.now();
        return new Agency(
                OrganizationId.generate(),
                organizationId,
                tenantId,
                name,
                commerceRegistryNumber,
                primaryCurrency,
                now,
                now
        );
    }

    // ─── Business methods ────────────────────────────────────────────────────

    /**
     * Updates the agency's name and registry number.
     *
     * @param newName                   updated operating name
     * @param newCommerceRegistryNumber updated registry number
     */
    public void updateIdentity(String newName, String newCommerceRegistryNumber) {
        this.name = newName;
        this.commerceRegistryNumber = newCommerceRegistryNumber;
        this.updatedAt = Instant.now();
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    public OrganizationId getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getCommerceRegistryNumber() { return commerceRegistryNumber; }
    public String getPrimaryCurrency() { return primaryCurrency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
