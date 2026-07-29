package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Local enrichment of a Freelancer (livreur) within the Go-Freelancer context.
 *
 * <p><strong>Composition design:</strong> {@code GofpFreelancer} composes with
 * {@link GofpUser} via {@code coreUserId} for all base identity data (name,
 * email, phone, profile photo, role, etc.).
 *
 * <p>The {@code coreFreelancerId} is the FK to the authoritative
 * {@code freelancers} / {@code delivery_persons} table in the ATANGA backend.
 *
 * <p><strong>Double address:</strong>
 * <ul>
 *   <li>{@code residenceAddressId} — where the freelancer lives (HOME).
 *       Used for proximity search when no operational base is set.</li>
 *   <li>{@code operationalBaseAddressId} — the freelancer's starting point
 *       for deliveries (WORK / BASE). Takes priority over residence for
 *       matching and routing.</li>
 * </ul>
 * Both are FK → {@code addresses.id}. The distinction is stored here rather
 * than in the generic {@code person_addresses} join table so it can be
 * queried directly without a join.
 *
 * @author François-Charles ATANGA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofp_freelancers")
public class GofpFreelancer implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    /** FK → freelancers.id (or delivery_persons.id) in ATANGA backend. */
    @NotNull
    @Column("core_freelancer_id")
    private UUID coreFreelancerId;

    /**
     * FK → gofp_users.core_user_id.
     * Composition link: identity (name, email, phone, CNI, NUI…) lives in GofpUser.
     */
    @NotNull
    @Column("core_user_id")
    private UUID coreUserId;

    // ── Professional identity ─────────────────────────────────────────────

    @Column("commercial_name")
    private String commercialName;

    @Column("commercial_register")
    private String commercialRegister;

    @Column("taxpayer_number")
    private String taxpayerNumber;

    /**
     * SIRET / equivalent national business ID.
     * Was missing from the core — now persisted locally for legal traceability.
     */
    @Column("siret")
    private String siret;

    // ── Status & activation ───────────────────────────────────────────────

    @NotNull
    @Column("status")
    @Builder.Default
    private FreelancerStatus status = FreelancerStatus.PENDING;

    /**
     * Operational activation flag.
     * A APPROVED freelancer can still be deactivated temporarily (e.g. on vacation).
     * Was missing from the core — now persisted locally.
     */
    @NotNull
    @Column("is_active")
    @Builder.Default
    private Boolean isActive = false;

    // ── Performance metrics ───────────────────────────────────────────────

    /** Average rating across all evaluations. */
    @Column("rating")
    private Double rating;

    /** Total number of successfully completed deliveries. */
    @Column("total_deliveries")
    @Builder.Default
    private Integer totalDeliveries = 0;

    /**
     * Number of deliveries that ended in FAILED status.
     * Was missing from the core — used for reliability scoring and quota enforcement.
     */
    @Column("failed_deliveries")
    @Builder.Default
    private Integer failedDeliveries = 0;

    /** Remaining delivery quota for the current subscription period. */
    @Column("remaining_deliveries")
    private Integer remainingDeliveries;

    // ── Financials ────────────────────────────────────────────────────────

    @Column("commission_rate")
    private Double commissionRate;

    @Column("subscription_id")
    private UUID subscriptionId;

    // ── Real-time GPS ─────────────────────────────────────────────────────

    @Column("latitude_gps")
    private Float latitudeGps;

    @Column("longitude_gps")
    private Float longitudeGps;

    // ── Double address ────────────────────────────────────────────────────

    /**
     * Residential address (HOME) — FK → addresses.id.
     * Fallback location for proximity matching when no operational base is set.
     */
    @Column("residence_address_id")
    private UUID residenceAddressId;

    /**
     * Operational base address (WORK / BASE) — FK → addresses.id.
     * Starting point for delivery assignments and geo-matching.
     * Takes priority over residenceAddressId.
     */
    @Column("operational_base_address_id")
    private UUID operationalBaseAddressId;

    // ── Transient composition (loaded by service, not R2DBC) ─────────────

    /** Full user identity — populated by service layer, not persisted here. */
    @Transient
    private GofpUser user;

    // ── Timestamps ────────────────────────────────────────────────────────

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
