package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity;

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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC entity mapped to tnt_market.market_orders.
 *
 * <p>Nested value objects on {@code MarketOrder} (OrderPricing, PaymentInfo,
 * DeliveryRequest/Address/ParcelSpec) are flattened into prefixed columns —
 * same strategy the original {@code MarketOrderEntity} used in the standalone
 * tiibntick-market-backend app, extended here to persist the full Address VO
 * (district/postalCode/landmark) since the domain model now carries it.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_market", value = "market_orders")
public class MarketOrderEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private String tenantId;

    @Column("client_id")
    private UUID clientId;

    @Column("provider_id")
    private UUID providerId;

    @Column("listing_id")
    private UUID listingId;

    @Column("offer_id")
    private UUID offerId;

    @Column("quote_request_id")
    private UUID quoteRequestId;

    @Column("status")
    private String status;

    @Column("mission_id")
    private UUID missionId;

    @Column("invoice_id")
    private UUID invoiceId;

    @Column("cancellation_reason")
    private String cancellationReason;

    @Column("cancelled_at")
    private LocalDateTime cancelledAt;

    // ---- Pricing (OrderPricing) ----

    @Column("base_amount")
    private Long baseAmount;

    @Column("distance_fee")
    private Long distanceFee;

    @Column("weight_fee")
    private Long weightFee;

    @Column("insurance_fee")
    private Long insuranceFee;

    @Column("express_fee")
    private Long expressFee;

    @Column("discount_amount")
    private Long discountAmount;

    @Column("total_amount")
    private Long totalAmount;

    @Column("currency")
    private String currency;

    // ---- Payment (PaymentInfo) ----

    @Column("payment_method")
    private String paymentMethod;

    @Column("transaction_ref")
    private String transactionRef;

    @Column("paid_at")
    private LocalDateTime paidAt;

    @Column("paid_amount")
    private Long paidAmount;

    @Column("mobile_money_phone")
    private String mobileMoneyPhone;

    // ---- Pickup address ----

    @Column("pickup_street")
    private String pickupStreet;

    @Column("pickup_district")
    private String pickupDistrict;

    @Column("pickup_city")
    private String pickupCity;

    @Column("pickup_country")
    private String pickupCountry;

    @Column("pickup_postal_code")
    private String pickupPostalCode;

    @Column("pickup_lat")
    private Double pickupLat;

    @Column("pickup_lng")
    private Double pickupLng;

    @Column("pickup_landmark")
    private String pickupLandmark;

    // ---- Delivery address ----

    @Column("delivery_street")
    private String deliveryStreet;

    @Column("delivery_district")
    private String deliveryDistrict;

    @Column("delivery_city")
    private String deliveryCity;

    @Column("delivery_country")
    private String deliveryCountry;

    @Column("delivery_postal_code")
    private String deliveryPostalCode;

    @Column("delivery_lat")
    private Double deliveryLat;

    @Column("delivery_lng")
    private Double deliveryLng;

    @Column("delivery_landmark")
    private String deliveryLandmark;

    // ---- Parcel spec ----

    @Column("parcel_description")
    private String parcelDescription;

    @Column("weight_kg")
    private Double weightKg;

    @Column("length_cm")
    private Double lengthCm;

    @Column("width_cm")
    private Double widthCm;

    @Column("height_cm")
    private Double heightCm;

    @Column("value_xaf")
    private Double valueXaf;

    @Column("fragile")
    private boolean fragile;

    @Column("perishable")
    private boolean perishable;

    @Column("requires_insurance")
    private boolean requiresInsurance;

    @Column("quantity")
    private int quantity;

    // ---- Delivery request meta ----

    @Column("desired_pickup_at")
    private LocalDateTime desiredPickupAt;

    @Column("desired_delivery_at")
    private LocalDateTime desiredDeliveryAt;

    @Column("urgency")
    private String urgency;

    @Column("special_instructions")
    private String specialInstructions;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private long version;
}
