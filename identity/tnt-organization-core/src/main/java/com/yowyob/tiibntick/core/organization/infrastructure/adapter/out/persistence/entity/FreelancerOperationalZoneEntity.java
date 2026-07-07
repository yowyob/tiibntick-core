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

import java.util.UUID;

/**
 * R2DBC persistence entity for operational zones declared by a FreelancerOrganization.
 *
 * <p>Maps to the {@code tnt_freelancer_operational_zone} table.
 * Each row represents one {@link com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone}
 * belonging to a FreelancerOrganization.
 *
 * <p>The WKT polygon is stored as TEXT (PostGIS-compatible via
 * {@code ST_GeomFromText(polygon_wkt, 4326)}). It enables spatial queries such as
 * {@code ST_DWithin} for proximity searches.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tnt_freelancer_operational_zone")
public class FreelancerOperationalZoneEntity implements Persistable<UUID> {

    /** Synthetic primary key. */
    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    /** FK to the owning FreelancerOrganization. */
    @Column("freelancer_org_id")
    private UUID freelancerOrgId;

    /** Human-readable zone name (e.g., "Quartier Bastos"). */
    @Column("zone_name")
    private String zoneName;

    /**
     * WKT POLYGON defining the zone boundary (SRID 4326).
     * Example: {@code POLYGON((3.85 11.5, 3.86 11.5, 3.86 11.51, 3.85 11.51, 3.85 11.5))}
     */
    @Column("polygon_wkt")
    private String polygonWkt;

    /** Whether this zone is currently active. */
    @Column("active")
    private boolean active;

    /** Access difficulty level enum name (LOW, MEDIUM, HIGH, VERY_HIGH). */
    @Column("access_difficulty")
    private String accessDifficulty;

    /** Zone type enum name (URBAN, PERI_URBAN, RURAL, DIPLOMATIC, PORT_ZONE). */
    @Column("zone_type")
    private String zoneType;
}
