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
 * R2DBC persistence entity for the {@link com.yowyob.tiibntick.core.organization.domain.model.Branch} aggregate.
 *
 * <p>Maps to the {@code tnt_branch} table. This entity is a data carrier in the
 * infrastructure adapter layer only — no domain logic.
 *
 * <p>Key columns:
 * <ul>
 *   <li>{@code id}               — TiiBnTick internal primary key (UUID).</li>
 *   <li>{@code organization_id}  — Kernel integration key (RT-comops organization UUID). NOT NULL.</li>
 *   <li>{@code agency_id}        — Foreign key to {@code tnt_agency.id}. NOT NULL.</li>
 *   <li>{@code tenant_id}        — Multi-tenant isolation column. NOT NULL.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tnt_branch")
public class BranchEntity implements Persistable<UUID> {

    /** TiiBnTick internal branch primary key. */
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

    /** Foreign key to the parent Agency ({@code tnt_agency.id}). */
    @Column("agency_id")
    private UUID agencyId;

    /** Multi-tenant isolation column. */
    @Column("tenant_id")
    private UUID tenantId;

    /** Branch display name. */
    @Column("name")
    private String name;

    /** Physical address (free-form — may be informal in Cameroonian context). */
    @Column("address")
    private String address;

    /**
     * Optional coverage zone name (denormalized from ServiceZone VO for
     * simplified persistence — WKT polygon stored separately).
     */
    @Column("service_zone_name")
    private String serviceZoneName;

    /**
     * WKT polygon string for the coverage zone (SRID 4326).
     * Null if no zone is assigned.
     */
    @Column("service_zone_wkt")
    private String serviceZoneWkt;

    /** Whether this branch is currently operational. */
    @Column("active")
    private boolean active;

    /** Record creation timestamp (UTC). */
    @Column("created_at")
    private Instant createdAt;

    /** Last modification timestamp (UTC). */
    @Column("updated_at")
    private Instant updatedAt;
}
