package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryUrgency;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents a delivery execution.
 * Time windows are defined using minimum and maximum bounds.
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

    @Column("freelancer_id")
    private UUID freelancerId;

    @Column("status")
    private DeliveryStatus status;

    @Column("delivery_need_id")
    private UUID deliveryNeedId;

    @Column("tarif")
    private Double tarif;

    @Column("note_livreur")
    private Double noteLivreur;

    @NotNull
    @Column("pickup_min_time")
    private Instant pickupMinTime;

    @NotNull
    @Column("pickup_max_time")
    private Instant pickupMaxTime;

    @NotNull
    @Column("delivery_min_time")
    private Instant deliveryMinTime;

    @NotNull
    @Column("delivery_max_time")
    private Instant deliveryMaxTime;

    @Column("delivery_note")
    private Double deliveryNote;

    // ── OTP confirmation fields ──────────────────────────────────────────

    /** BCrypt hash of the 6-digit code given to the shipper at parcel creation.
     *  The delivery person must provide this code to transition to PICKED_UP. */
    @Column("pickup_otp_hash")
    private String pickupOtpHash;

    /** BCrypt hash of the 6-digit code sent to the recipient at parcel creation.
     *  The delivery person must provide this code to transition to DELIVERED (direct). */
    @Column("delivery_otp_hash")
    private String deliveryOtpHash;

    /** Timestamp when the delivery person physically collected the parcel (PICKED_UP confirmed). */
    @Column("actual_pickup_time")
    private Instant actualPickupTime;

    /** Timestamp when the recipient confirmed receipt of the parcel (DELIVERED confirmed). */
    @Column("actual_delivery_time")
    private Instant actualDeliveryTime;
}