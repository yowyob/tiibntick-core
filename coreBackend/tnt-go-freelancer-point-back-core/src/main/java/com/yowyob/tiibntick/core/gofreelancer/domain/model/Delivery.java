package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The Go-Freelancer extension of a delivery: what the product adds to the delivery that
 * {@code tnt-delivery-core} owns under the same id (OTP hashes, handover codes, receive code, notes,
 * the links to the announcement and the delivery need).
 *
 * <p>The delivery's own facts are <em>not</em> stored here: status, assigned delivery person, real
 * pickup / delivery instants, agreed price and pickup / hand-over windows belong to the core. They are
 * exposed by the read-only accessors below, filled from the core whenever the row is loaded by
 * {@code GofpDeliveryRepositoryAdapter}; they have no setter and are never written to this table. They
 * are {@code null} for a row the core does not know.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("deliveries")
public class Delivery implements Persistable<UUID>, TntPersistableEntity {
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    @Column("announcement_id")
    private UUID announcementId;

    @Column("delivery_need_id")
    private UUID deliveryNeedId;

    @Column("note_livreur")
    private Double noteLivreur;

    @Column("delivery_note")
    private Double deliveryNote;

    // ── OTP confirmation fields ──────────────────────────────────────────

    /** BCrypt hash of the 6-digit code given to the shipper at parcel creation.
     *  The delivery person must provide this code to transition to PICKED_UP. */
    @JsonIgnore
    @Column("pickup_otp_hash")
    private String pickupOtpHash;

    /** BCrypt hash of the 6-digit code sent to the recipient at parcel creation.
     *  The delivery person must provide this code to transition to DELIVERED (direct). */
    @JsonIgnore
    @Column("delivery_otp_hash")
    private String deliveryOtpHash;

    /** Lot C-53 — pickup code encrypted (AES-256-GCM), readable again by the owner of the need only. */
    @JsonIgnore
    @Column("pickup_code_enc")
    private String pickupCodeEnc;

    /** Lot C-53 — delivery code encrypted (AES-256-GCM), readable again by the owner of the need only. */
    @JsonIgnore
    @Column("delivery_code_enc")
    private String deliveryCodeEnc;

    // ── Kernel escrow payment (lot L5) ───────────────────────────────────────

    /** Actor (Kernel user) of the courier paid by the escrow; printed on the invoice. */
    @Column("driver_user_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UUID driverUserId;

    // ── Read-through from tnt-delivery-core (never persisted here) ───────────

    /** Lifecycle status owned by the core. */
    @Transient
    @Setter(AccessLevel.NONE)
    private DeliveryStatus status;

    /** The delivery person the core assigned (the id the selected response carried). */
    @Transient
    @Setter(AccessLevel.NONE)
    private UUID freelancerId;

    /** Agreed price (core {@code agreedAmount}). */
    @Transient
    @Setter(AccessLevel.NONE)
    private Double tarif;

    @Transient
    @Setter(AccessLevel.NONE)
    private Instant pickupMinTime;

    @Transient
    @Setter(AccessLevel.NONE)
    private Instant pickupMaxTime;

    @Transient
    @Setter(AccessLevel.NONE)
    private Instant deliveryMinTime;

    @Transient
    @Setter(AccessLevel.NONE)
    private Instant deliveryMaxTime;

    /** When the delivery person physically collected the parcel (PICKED_UP confirmed in the core). */
    @Transient
    @Setter(AccessLevel.NONE)
    private Instant actualPickupTime;

    /** When the recipient received the parcel (DELIVERED confirmed in the core). */
    @Transient
    @Setter(AccessLevel.NONE)
    private Instant actualDeliveryTime;

    /**
     * Fills the read-through facts with what the core says. Called by the repository adapter and by the
     * services that just drove a transition; it changes nothing that is persisted.
     */
    public Delivery readThrough(DeliveryStatus status, UUID deliveryPersonId, BigDecimal agreedAmount,
                                Instant pickupWindowStart, Instant pickupWindowEnd,
                                Instant deliveryWindowStart, Instant deliveryWindowEnd,
                                Instant actualPickupTime, Instant actualDeliveryTime) {
        this.status = status;
        this.freelancerId = deliveryPersonId;
        this.tarif = agreedAmount != null ? agreedAmount.doubleValue() : null;
        this.pickupMinTime = pickupWindowStart;
        this.pickupMaxTime = pickupWindowEnd;
        this.deliveryMinTime = deliveryWindowStart;
        this.deliveryMaxTime = deliveryWindowEnd;
        this.actualPickupTime = actualPickupTime;
        this.actualDeliveryTime = actualDeliveryTime;
        return this;
    }
}
