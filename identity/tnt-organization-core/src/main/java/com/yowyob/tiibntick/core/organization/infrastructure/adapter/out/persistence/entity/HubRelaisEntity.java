package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@link com.yowyob.tiibntick.core.organization.domain.model.HubRelais} aggregate.
 *
 * <p>Maps to the {@code tnt_hub_relais} table. The {@code geographic_point_wkt} column
 * stores the relay hub's location as a PostGIS-compatible WKT POINT string (SRID 4326),
 * enabling native spatial queries from {@code HubRelaisR2dbcRepository}.
 *
 * <p>Key columns:
 * <ul>
 *   <li>{@code id}                   — TiiBnTick internal primary key (UUID).</li>
 *   <li>{@code organization_id}      — Kernel integration key (RT-comops organization UUID). NOT NULL.</li>
 *   <li>{@code tenant_id}            — Multi-tenant isolation column. NOT NULL.</li>
 *   <li>{@code geographic_point_wkt} — PostGIS WKT POINT (SRID 4326), e.g., {@code POINT(9.7022 4.0511)}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tnt_hub_relais")
public class HubRelaisEntity implements Persistable<UUID> {

    /** TiiBnTick internal relay hub primary key. */
    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    /**
     * Kernel integration key.
     * References the Organization entity in RT-comops-organization-core.
     * Persisted as {@code organization_id UUID NOT NULL}.
     */
    @Column("organization_id")
    private UUID organizationId;

    /** Multi-tenant isolation column. */
    @Column("tenant_id")
    private UUID tenantId;

    /** Display name of the relay hub. */
    @Column("name")
    private String name;

    /** Maximum number of parcels this hub can store simultaneously. */
    @Column("max_parcel_capacity")
    private int maxParcelCapacity;

    /**
     * Geographic position as a PostGIS WKT POINT string (SRID 4326).
     * Format: {@code POINT(longitude latitude)}
     */
    @Column("geographic_point_wkt")
    private String geographicPointWkt;

    /** Free-text opening hours (e.g., "Mon-Sat 08:00-18:00"). */
    @Column("opening_hours")
    private String openingHours;

    /**
     * Operator actor UUID — references a TiiBnTick actor in tnt-actor-core.
     * Nullable: some hubs may be unassigned.
     */
    @Column("operator_id")
    private UUID operatorId;

    /** Whether this hub is currently accepting parcels. */
    @Column("operational")
    private boolean operational;

    /** Record creation timestamp (UTC). */
    @Column("created_at")
    private Instant createdAt;

    /** Last modification timestamp (UTC). */
    @Column("updated_at")
    private Instant updatedAt;
}
