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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * R2DBC entity mapped to tnt_market.service_offers.
 *
 * <p>Nested value objects that don't map to flat columns (the set of
 * {@code daysOfWeek} and the list of {@code exceptionalClosures} making up
 * {@code OfferAvailability}) are serialized as JSON text — see
 * {@link com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper.ServiceOfferMapper},
 * ported from the original {@code ServiceOfferMapper}'s strategy.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_market", value = "service_offers")
public class ServiceOfferEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private String tenantId;

    @Column("listing_id")
    private UUID listingId;

    @Column("provider_id")
    private UUID providerId;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("service_type")
    private String serviceType;

    @Column("status")
    private String status;

    // Pricing
    @Column("base_price")
    private BigDecimal basePrice;

    @Column("per_km_rate")
    private BigDecimal perKmRate;

    @Column("per_kg_rate")
    private BigDecimal perKgRate;

    @Column("currency")
    private String currency;

    @Column("minimum_price")
    private BigDecimal minimumPrice;

    @Column("maximum_price")
    private BigDecimal maximumPrice;

    @Column("pricing_dsl_expression")
    private String pricingDslExpression;

    // Constraints
    @Column("max_weight_kg")
    private Double maxWeightKg;

    @Column("max_length_cm")
    private Double maxLengthCm;

    @Column("max_width_cm")
    private Double maxWidthCm;

    @Column("max_height_cm")
    private Double maxHeightCm;

    @Column("max_value_xaf")
    private Double maxValueXaf;

    @Column("accepts_fragile")
    private boolean acceptsFragile;

    @Column("accepts_perishable")
    private boolean acceptsPerishable;

    @Column("accepts_hazardous")
    private boolean acceptsHazardous;

    @Column("requires_insurance")
    private boolean requiresInsurance;

    @Column("max_distance_km")
    private Double maxDistanceKm;

    // Availability
    /** JSON array of {@link java.time.DayOfWeek} names. */
    @Column("days_of_week")
    private String daysOfWeek;

    @Column("open_time")
    private LocalTime openTime;

    @Column("close_time")
    private LocalTime closeTime;

    /** JSON array of ISO {@link java.time.LocalDate} strings. */
    @Column("exceptional_closures")
    private String exceptionalClosures;

    @Column("express_available")
    private boolean expressAvailable;

    @Column("same_day_available")
    private boolean sameDayAvailable;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private long version;
}
