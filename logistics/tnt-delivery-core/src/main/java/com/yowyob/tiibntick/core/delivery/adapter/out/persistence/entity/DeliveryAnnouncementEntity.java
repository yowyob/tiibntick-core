package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@code DeliveryAnnouncement} aggregate.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_delivery_announcements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAnnouncementEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("client_id")
    private UUID clientId;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("offered_amount")
    private BigDecimal offeredAmount;

    @Column("currency")
    private String currency;

    @Column("pricing_mode")
    private String pricingMode;

    @Column("parcel_id")
    private UUID parcelId;

    @Column("status")
    private String status;

    @Column("urgency")
    private String urgency;

    // Pickup address
    @Column("pickup_street")    private String pickupStreet;
    @Column("pickup_landmark")  private String pickupLandmark;
    @Column("pickup_district")  private String pickupDistrict;
    @Column("pickup_city")      private String pickupCity;
    @Column("pickup_country")   private String pickupCountry;
    @Column("pickup_latitude")  private Double pickupLatitude;
    @Column("pickup_longitude") private Double pickupLongitude;

    // Delivery address
    @Column("delivery_street")    private String deliveryStreet;
    @Column("delivery_landmark")  private String deliveryLandmark;
    @Column("delivery_district")  private String deliveryDistrict;
    @Column("delivery_city")      private String deliveryCity;
    @Column("delivery_country")   private String deliveryCountry;
    @Column("delivery_latitude")  private Double deliveryLatitude;
    @Column("delivery_longitude") private Double deliveryLongitude;

    // Recipient
    @Column("recipient_name")      private String recipientName;
    @Column("recipient_phone")     private String recipientPhone;
    @Column("recipient_alt_phone") private String recipientAltPhone;

    // Sender contact (optional)
    @Column("sender_name")  private String senderName;
    @Column("sender_phone") private String senderPhone;
    @Column("sender_email") private String senderEmail;

    // Recipient e-mail (optional)
    @Column("recipient_email") private String recipientEmail;

    // Generic details (optional)
    @Column("required_vehicle_type")   private String requiredVehicleType;
    @Column("payment_method")          private String paymentMethod;
    @Column("transport_method")        private String transportMethod;
    @Column("distance_km")             private BigDecimal distanceKm;
    @Column("estimated_duration_min")  private Integer estimatedDurationMin;
    @Column("destination_hub_id")      private UUID destinationHubId;
    @Column("handover_signature_ref")  private String handoverSignatureRef;
    @Column("service_fee")             private BigDecimal serviceFee;
    @Column("origin_ref_type")         private String originRefType;
    @Column("origin_ref_id")           private UUID originRefId;

    @Column("selected_response_id")
    private UUID selectedResponseId;

    @Column("created_delivery_id")
    private UUID createdDeliveryId;

    // ── lot evt-l2 (spec §1.5) ────────────────────────────────────────────────────────────────
    @Column("kind")
    private String kind;
    @Column("creator_role")
    private String creatorRole;
    @Column("outcome")
    private String outcome;
    @Column("published_at")
    private Instant publishedAt;
    @Column("assigned_at")
    private Instant assignedAt;
    @Column("closed_at")
    private Instant closedAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Long version;
}
