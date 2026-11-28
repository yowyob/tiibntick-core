package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.TrackingCode;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
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

/**
 * Represents a delivery need emitted by a user.
 * A user only needs basic credentials to create a delivery need.
 * Similar to an announcement but without pricing or explicit shipper/recipient.
 * This class uses the delivery-core {@link TrackingCode} value object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("delivery_needs")
public class DeliveryNeed implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    @NotNull
    @Column("user_id")
    private UUID userId;

    /**
     * Not yet present on {@code public.delivery_needs} — kept for domain use only.
     * @author MANFOUO BRAUN
     */
    @Transient
    private TrackingCode trackingCode;

    /**
     * Id of the core {@code Parcel} of this need (lot L1: the goods are a core parcel). Stored in
     * {@code parcel_id} (lot L2; the legacy {@code packet_id} column is no longer written and goes at
     * closure). The Java name and the JSON field stay {@code packetId}.
     */
    @Column("parcel_id")
    private UUID packetId;

    /**
     * Announcement the core holds for this need (denormalised pointer; the authoritative link is the
     * announcement's origin reference). Null while the need is not published.
     */
    @Column("announcement_id")
    private UUID announcementId;

    /** Destination relay point chosen by the sender (gofp relay point id or its hub id), if any. */
    @Column("target_relay_point_id")
    private UUID targetRelayPointId;

    @Column("requested_storage_days")
    private Integer requestedStorageDays;

    @Column("sender_first_name") // lot C-56 — persisted (was @Transient: typed by the sender, then lost)
    private String senderFirstName;

    @Column("sender_last_name") // lot C-56 — persisted (was @Transient: typed by the sender, then lost)
    private String senderLastName;

    @Transient
    private String senderEmail;

    @Column("sender_phone") // lot C-56 — persisted (was @Transient: typed by the sender, then lost)
    private String senderPhone;

    @Column("recipient_first_name") // lot C-56 — persisted (was @Transient: typed by the sender, then lost)
    private String recipientFirstName;

    @Column("recipient_last_name") // lot C-56 — persisted (was @Transient: typed by the sender, then lost)
    private String recipientLastName;

    @Transient
    private String recipientEmail;

    @Column("recipient_phone") // lot C-56 — persisted (was @Transient: typed by the sender, then lost)
    private String recipientPhone;

    @NotNull
    @Column("pickup_address_id")
    private UUID pickupAddressId;

    @NotNull
    @Column("delivery_address_id")
    private UUID deliveryAddressId;

    @NotNull
    @Column("title")
    private String title;

    @Column("description")
    private String description;

    /**
     * Derived, never stored: computed from the announcement and delivery the core holds for this
     * need (see {@code DeliveryNeedStatusPolicy}); filled when the need is read. Null on a need that
     * was not read through {@code IDeliveryNeedRepository}.
     */
    @Transient
    private DeliveryNeedStatus status;

    /**
     * QO-9: how a {@code COMPLETED} need ended — {@code DELIVERED} or {@code FAILED} (the delivery failed for good);
     * {@code null} while the need is not completed. Derived like {@link #status}.
     */
    @Transient
    private String outcome;

    @Column("duration")
    private Integer duration;

    @Column("signature_url")
    private String signatureUrl;

    @Column("payment_method")
    private String paymentMethod;

    @Column("transport_method")
    private String transportMethod;

    /** Lot C-54 — vehicle the sender asks for (canonical core type: MOTORBIKE, TRICYCLE, CAR, TRUCK, BUS); null = any. */
    @Column("required_vehicle_type")
    private String requiredVehicleType;

    @Column("distance")
    private Double distance;

    /** Derived, never stored: the delivery the core created from this need's announcement. */
    @Transient
    private UUID deliveryId;

    /** Derived, never stored: the delivery person whose response the client selected. */
    @Transient
    private UUID assignedFreelancerId;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("pickup_deadline")
    private java.time.LocalDateTime pickupDeadline;
}
