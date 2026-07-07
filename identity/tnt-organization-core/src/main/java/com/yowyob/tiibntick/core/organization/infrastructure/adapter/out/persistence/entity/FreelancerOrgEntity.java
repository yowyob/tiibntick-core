package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the
 * {@link com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization} aggregate.
 *
 * <p>Maps to the {@code tnt_freelancer_organization} table. This entity is a pure
 * data carrier — no domain logic. Collections (zones, sub-deliverers) are persisted
 * in separate tables via dedicated entities.
 *
 * <p>Key columns:
 * <ul>
 *   <li>{@code id}             — TiiBnTick internal primary key (UUID).</li>
 *   <li>{@code tenant_id}      — Multi-tenant key, prefixed "FRL-{uuid}". UNIQUE, NOT NULL.</li>
 *   <li>{@code owner_actor_id} — OWNER actor UUID (from tnt-actor-core). NOT NULL.</li>
 * </ul>
 *
 * <p>Capabilities (maxWeightKg, maxDistanceKm, worksWeekends, worksNights,
 * acceptedPackageTypeCodes, specializationCodes) are stored as scalar columns —
 * the {@link com.yowyob.tiibntick.core.organization.domain.vo.FreelancerCapabilities}
 * VO is denormalized here for simplicity and efficient filtering.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tnt_freelancer_organization")
public class FreelancerOrgEntity implements Persistable<UUID> {

    /** TiiBnTick internal primary key. */
    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    /**
     * Optional Kernel Organization reference (RT-comops-organization-core UUID).
     * Nullable — FreelancerOrgs can be registered without a Kernel org link.
     */
    @Column("organization_id")
    private UUID organizationId;

    /**
     * Multi-tenant isolation key. Prefixed "FRL-{uuid}".
     * UNIQUE constraint in the database.
     */
    @Column("tenant_id")
    private String tenantId;

    /** Commercial trade name displayed to clients. */
    @Column("trade_name")
    private String tradeName;

    /** OWNER actor UUID (from tnt-actor-core). NOT NULL. */
    @Column("owner_actor_id")
    private UUID ownerActorId;

    /** Registration status enum name. */
    @Column("registration_status")
    private String registrationStatus;

    /** KYC verification level enum name. */
    @Column("kyc_level")
    private String kycLevel;

    // ─── FreelancerBillingProfile (denormalized) ─────────────────────────────

    /** UUID of the active BillingPolicy (nullable). */
    @Column("active_policy_id")
    private UUID activePolicyId;

    /** Template code used to generate the active policy (nullable). */
    @Column("default_template_code")
    private String defaultTemplateCode;

    /** Whether the freelancer is VAT-registered. */
    @Column("vat_applicable")
    private boolean vatApplicable;

    /** NUI or equivalent tax identifier (nullable). */
    @Column("tax_id")
    private String taxId;

    // ─── Trust ───────────────────────────────────────────────────────────────

    /** Aggregated trust score (0.0–5.0). */
    @Column("trust_score")
    private double trustScore;

    /** Blockchain DID (nullable until VERIFIED). */
    @Column("blockchain_did")
    private String blockchainDid;

    // ─── FreelancerCapabilities (denormalized) ────────────────────────────────

    /** Maximum parcel weight in kg. */
    @Column("max_weight_kg")
    private double maxWeightKg;

    /** Maximum delivery distance in km. */
    @Column("max_distance_km")
    private double maxDistanceKm;

    /** Whether weekend missions are accepted. */
    @Column("works_weekends")
    private boolean worksWeekends;

    /** Whether night deliveries are accepted. */
    @Column("works_nights")
    private boolean worksNights;

    /**
     * Comma-separated PackageType codes accepted by this org.
     * Example: "STANDARD,FRAGILE,PERISHABLE"
     */
    @Column("accepted_package_type_codes")
    private String acceptedPackageTypeCodes;

    /**
     * Comma-separated FreelancerSpecialization enum names.
     * Example: "MEDICAL_DELIVERY,REFRIGERATED"
     */
    @Column("specialization_codes")
    private String specializationCodes;

    // ─── Audit ───────────────────────────────────────────────────────────────

    /** Record creation timestamp (UTC). */
    @Column("created_at")
    private Instant createdAt;

    /** Last modification timestamp (UTC). */
    @Column("updated_at")
    private Instant updatedAt;

    /** Optimistic locking version (managed by Spring Data R2DBC). */
    @Version
    @Column("version")
    private int version;
}
