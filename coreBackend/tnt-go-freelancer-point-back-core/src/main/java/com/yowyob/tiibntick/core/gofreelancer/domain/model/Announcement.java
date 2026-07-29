package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
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
 * Represents a delivery announcement published by a client.
 *
 * <p>Enriched with the fields that were missing from the initial core version:
 * {@code clientId}, {@code packetId}, addresses, {@code title}, {@code description},
 * {@code status}, {@code amount}, and timestamps.
 * {@code recipientId} and {@code shipperId} are intentionally excluded — contacts
 * are managed through the GofpUser/DeliveryNeed structures.
 *
 * @author François-Charles ATANGA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("announcements")
public class Announcement implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    // ── Ownership & linked entities ───────────────────────────────────────────

    /** FK → clients.id — the client who published this announcement. */
    @Column("client_id")
    private UUID clientId;

    /** FK → packets.id — the parcel to be delivered. */
    @Column("packet_id")
    private UUID packetId;

    /** FK → addresses.id — where the parcel is picked up. */
    @NotNull
    @Column("pickup_address_id")
    private UUID pickupAddressId;

    /** FK → addresses.id — where the parcel must be delivered. */
    @NotNull
    @Column("delivery_address_id")
    private UUID deliveryAddressId;

    // ── Content ───────────────────────────────────────────────────────────────

    @NotNull
    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @NotNull
    @Column("status")
    private AnnouncementStatus status;

    // ── Logistics ─────────────────────────────────────────────────────────────

    @Column("duration")
    private Integer duration;

    @Column("distance")
    private Double distance;

    @Column("transport_method")
    private String transportMethod;

    @Column("required_vehicle_type")
    private String requiredVehicleType;

    @Column("assigned_freelancer_id")
    private UUID assignedFreelancerId;

    @Column("destination_relay_point_id")
    private UUID destinationRelayPointId;

    // ── Contacts (Sender / Recipient) ─────────────────────────────────────────

    @Column("shipper_first_name")
    private String shipperFirstName;

    @Column("shipper_last_name")
    private String shipperLastName;

    @Column("shipper_email")
    private String shipperEmail;

    @Column("shipper_phone")
    private String shipperPhone;

    @Column("recipient_first_name")
    private String recipientFirstName;

    @Column("recipient_last_name")
    private String recipientLastName;

    @Column("recipient_email")
    private String recipientEmail;

    @Column("recipient_phone")
    private String recipientPhone;

    // ── Financials ────────────────────────────────────────────────────────────

    @Column("amount")
    private Double amount;

    @Column("currency")
    private String currency;

    @Column("logistics_price")
    private Double logisticsPrice;

    @Column("payment_method")
    private String paymentMethod;

    @Column("signature_url")
    private String signatureUrl;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
