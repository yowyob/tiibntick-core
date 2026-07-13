package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC entity mapped to {@code tnt_market.quote_requests}.
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code adapter.outbound.persistence.entity.QuoteRequestEntity}, adapted to
 * the {@link Persistable} + Lombok builder convention used across this
 * module (see {@code MarketListingEntity} in this same package).</p>
 *
 * <p>The embedded {@code DeliveryRequest} (pickup/delivery address + parcel
 * spec) is flattened into individual columns, same strategy as the original
 * entity. The embedded {@code List<QuoteResponse>} does not map to flat
 * columns; it is persisted as a JSON snapshot in {@code responses_json} for
 * durability/audit — see {@code MarketQuoteRequestPersistenceAdapter} for the
 * (currently write-only) mapping caveat inherited from the original adapter.</p>
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_market", value = "quote_requests")
public class QuoteRequestEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private String tenantId;

    @Column("client_id")
    private UUID clientId;

    @Column("listing_id")
    private UUID listingId;

    @Column("provider_id")
    private UUID providerId;

    @Column("status")
    private String status;

    @Column("notes")
    private String notes;

    @Column("cancellation_reason")
    private String cancellationReason;

    @Column("selected_response_id")
    private UUID selectedResponseId;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    // -------------------------------------------------------
    // DeliveryRequest — pickup address (flattened)
    // -------------------------------------------------------

    @Column("pickup_street")
    private String pickupStreet;

    @Column("pickup_district")
    private String pickupDistrict;

    @Column("pickup_city")
    private String pickupCity;

    @Column("pickup_lat")
    private Double pickupLat;

    @Column("pickup_lng")
    private Double pickupLng;

    // -------------------------------------------------------
    // DeliveryRequest — delivery address (flattened)
    // -------------------------------------------------------

    @Column("delivery_street")
    private String deliveryStreet;

    @Column("delivery_district")
    private String deliveryDistrict;

    @Column("delivery_city")
    private String deliveryCity;

    @Column("delivery_lat")
    private Double deliveryLat;

    @Column("delivery_lng")
    private Double deliveryLng;

    // -------------------------------------------------------
    // DeliveryRequest — parcel spec (flattened)
    // -------------------------------------------------------

    @Column("parcel_description")
    private String parcelDescription;

    @Column("weight_kg")
    private double weightKg;

    @Column("length_cm")
    private double lengthCm;

    @Column("width_cm")
    private double widthCm;

    @Column("height_cm")
    private double heightCm;

    @Column("value_xaf")
    private double valueXaf;

    @Column("fragile")
    private boolean fragile;

    @Column("perishable")
    private boolean perishable;

    @Column("requires_insurance")
    private boolean requiresInsurance;

    @Column("quantity")
    private int quantity;

    // -------------------------------------------------------
    // DeliveryRequest — schedule / urgency
    // -------------------------------------------------------

    @Column("desired_pickup_at")
    private LocalDateTime desiredPickupAt;

    @Column("desired_delivery_at")
    private LocalDateTime desiredDeliveryAt;

    @Column("urgency")
    private String urgency;

    @Column("special_instructions")
    private String specialInstructions;

    /** JSON snapshot of {@code List<QuoteResponse>} — see class javadoc. */
    @Column("responses_json")
    private String responsesJson;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
