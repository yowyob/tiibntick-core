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
import java.util.UUID;

/**
 * R2DBC entity mapped to tnt_market.market_campaigns.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_market", value = "market_campaigns")
public class MarketCampaignEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private String tenantId;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("campaign_type")
    private String campaignType;

    @Column("status")
    private String status;

    @Column("discount_type")
    private String discountType;

    @Column("discount_value")
    private BigDecimal discountValue;

    @Column("max_discount")
    private BigDecimal maxDiscount;

    @Column("min_order_amount")
    private BigDecimal minOrderAmount;

    @Column("currency")
    private String currency;

    /** JSON array of target listing UUID strings. */
    @Column("scope_provider_ids")
    private String scopeProviderIds;

    /** JSON array of target ServiceType enum names. */
    @Column("scope_service_types")
    private String scopeServiceTypes;

    /** JSON array of target ProviderType enum names. */
    @Column("scope_cities")
    private String scopeCities;

    @Column("applicable_to_all")
    private boolean applicableToAll;

    @Column("promo_code")
    private String promoCode;

    @Column("budget")
    private BigDecimal budget;

    @Column("spent_amount")
    private BigDecimal spentAmount;

    @Column("start_date")
    private LocalDateTime startDate;

    @Column("end_date")
    private LocalDateTime endDate;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private long version;
}
