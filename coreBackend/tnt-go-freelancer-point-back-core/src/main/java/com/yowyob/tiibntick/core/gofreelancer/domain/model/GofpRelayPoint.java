package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
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
import java.util.List;
import java.util.UUID;

/**
 * Local enrichment of a Relay Point within the Go-Freelancer context.
 *
 * <p><strong>Composition design:</strong> {@code GofpRelayPoint} composes with
 * {@link GofpFreelancer} (the relay point manager / owner) via
 * {@code coreFreelancerId} and exposes the owner's contact details directly
 * for fast access without an extra join.
 *
 * <p>The {@code coreRelayPointId} is the FK to the authoritative
 * {@code tnt-geo-core} {@code RelayHub.id} (hub identity source of truth).
 *
 * <p><strong>Double address:</strong>
 * <ul>
 *   <li>{@code postalAddressId} — official mailing / billing address (POSTAL).
 *       Used for invoicing and administrative purposes.</li>
 *   <li>{@code physicalAccessAddressId} — the address clients actually go to
 *       to pick up their parcel (PHYSICAL). Shown on maps, used for routing.</li>
 * </ul>
 * Both are FK → {@code addresses.id}.
 *
 * @author François-Charles ATANGA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Table("gofp_relay_points")
public class GofpRelayPoint implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default @JsonIgnore private boolean isNew = true;

    @Override
    @JsonIgnore
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    /** FK → tnt-geo-core RelayHub.id (authoritative hub identity). */
    @NotNull
    @Column("core_relay_point_id")
    private UUID coreRelayPointId;

    /**
     * FK → gofp_freelancers.core_freelancer_id.
     * The freelancer who owns and manages this relay point.
     */
    @NotNull
    @Column("core_freelancer_id")
    private UUID coreFreelancerId;

    // ── Owner contact (denormalised for fast notification lookup) ─────────
    // These mirror the owner's GofpUser fields so the relay point can be
    // contacted without joining through GofpFreelancer → GofpUser.

    /**
     * Owner's first name — was missing from the core, caused notification
     * failures when ATANGA was unavailable.
     */
    @Column("owner_first_name")
    private String ownerFirstName;

    @Column("owner_last_name")
    private String ownerLastName;

    /**
     * Owner's email — was missing from the core.
     * Used as fallback channel when push notification fails.
     */
    @Column("owner_email")
    private String ownerEmail;

    /**
     * Owner's phone — was missing from the core.
     * Used for SMS fallback and displayed to clients on the relay point detail page.
     */
    @Column("owner_phone")
    private String ownerPhone;

    // ── Relay point identity ──────────────────────────────────────────────

    @NotNull
    @Column("name")
    private String name;

    @NotNull
    @Column("status")
    @Builder.Default
    private RelayPointStatus status = RelayPointStatus.PENDING;

    @Column("is_active")
    @Builder.Default
    @JsonProperty("isActive")
    private Boolean isActive = false;

    /**
     * Average rating from client reviews.
     * Was missing from the core as a persisted field — only appeared in the
     * response DTO assembled on the fly.
     */
    @Column("rating")
    private Double rating;

    /** Total number of parcels successfully handled. */
    @Column("total_deposits")
    @Builder.Default
    private Integer totalDeposits = 0;

    // ── Subscription quota ────────────────────────────────────────────────

    @Column("subscription_id")
    private UUID subscriptionId;

    @Column("deposits_used")
    @Builder.Default
    private Integer depositsUsed = 0;

    @Column("max_deposits")
    private Integer maxDeposits;

    // ── Storage space dimensions ──────────────────────────────────────────

    /**
     * Length of the storage space (hangar/room) in the unit specified by
     * {@link #storageDimensionUnit}. Same pattern as {@code FreelancerVehicle.trunkLength}.
     */
    @Column("storage_length")
    private Double storageLength;

    /** Width of the storage space. */
    @Column("storage_width")
    private Double storageWidth;

    /** Height of the storage space. */
    @Column("storage_height")
    private Double storageHeight;

    /**
     * Unit for storage dimensions: "m", "cm", "mm", "in".
     * Defaults to "m" when null.
     * The total capacity in m³ is derived on-the-fly via
     * {@link com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerVehicleApplicationService#calculateVolumeInM3}.
     */
    @Column("storage_dimension_unit")
    private String storageDimensionUnit;

    // ── Double address ────────────────────────────────────────────────────

    /**
     * Postal / billing address (POSTAL) — FK → addresses.id.
     * Official mailing address used for invoicing and administrative records.
     */
    @Column("postal_address_id")
    private UUID postalAddressId;

    /**
     * Physical access address (PHYSICAL) — FK → addresses.id.
     * The exact location clients navigate to for parcel pickup.
     * Displayed on maps and used for routing.
     */
    @Column("physical_access_address_id")
    private UUID physicalAccessAddressId;

    // ── Transient composition (loaded by service, not R2DBC) ─────────────

    /** Opening hours — loaded by service layer, not persisted here. */
    @Transient
    private List<OpeningHours> openingHours;

    /** Pricing policy — loaded by service layer, not persisted here. */
    @Transient
    private RelayPointPricingPolicy pricingPolicy;

    /** Visuals (storefront photo, shop photo) — loaded by service layer. */
    @Transient
    private RelayPointVisuals visuals;

    /** Owner freelancer — loaded by service layer. */
    @Transient
    private GofpFreelancer owner;

    // ── Timestamps ────────────────────────────────────────────────────────

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
