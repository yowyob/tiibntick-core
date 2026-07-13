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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC entity mapped to tnt_market.merchant_contracts.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_market", value = "merchant_contracts")
public class MerchantContractEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private String tenantId;

    @Column("merchant_id")
    private UUID merchantId;

    @Column("provider_id")
    private UUID providerId;

    @Column("listing_id")
    private UUID listingId;

    @Column("status")
    private String status;

    /** JSON array of VolumeTier objects. */
    @Column("volume_tiers")
    private String volumeTiers;

    @Column("base_discount_pct")
    private double baseDiscountPct;

    @Column("max_monthly_orders")
    private int maxMonthlyOrders;

    @Column("min_monthly_orders")
    private int minMonthlyOrders;

    @Column("payment_term_days")
    private int paymentTermDays;

    @Column("dsl_expression_override")
    private String dslExpressionOverride;

    @Column("special_conditions")
    private String specialConditions;

    @Column("termination_reason")
    private String terminationReason;

    @Column("start_date")
    private LocalDate startDate;

    @Column("end_date")
    private LocalDate endDate;

    @Column("signed_at")
    private LocalDateTime signedAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private long version;
}
