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

    // NEW: Tracking code for logistics lifecycle
    @Column("tracking_code")
    private TrackingCode trackingCode;

    @Column("packet_id")
    private UUID packetId;

    @Column("target_relay_point_id")
    private UUID targetRelayPointId;

    @Column("requested_storage_days")
    private Integer requestedStorageDays;

    // ----- Expéditeur -----
    @Column("sender_first_name")
    private String senderFirstName;

    @Column("sender_last_name")
    private String senderLastName;

    @Column("sender_email")
    private String senderEmail;

    @Column("sender_phone")
    private String senderPhone;

    // ----- Destinataire -----
    @Column("recipient_first_name")
    private String recipientFirstName;

    @Column("recipient_last_name")
    private String recipientLastName;

    @Column("recipient_email")
    private String recipientEmail;

    @Column("recipient_phone")
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

    @NotNull
    @Column("status")
    private DeliveryNeedStatus status;

    @Column("duration")
    private Integer duration;

    @Column("signature_url")
    private String signatureUrl;

    @Column("payment_method")
    private String paymentMethod;

    @Column("transport_method")
    private String transportMethod;

    @Column("distance")
    private Double distance;

    @Column("delivery_id")
    private UUID deliveryId;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("pickup_deadline")
    private java.time.LocalDateTime pickupDeadline;
}
