package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC entity for PaymentSplit domain model.
 * Maps to the {@code wallet_payment_splits} table.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "billing", value = "wallet_payment_splits")
public class PaymentSplitEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("mission_id")
    private String missionId;

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("currency")
    @Builder.Default
    private String currency = "XAF";

    @Column("platform_commission")
    private BigDecimal platformCommission;

    @Column("org_revenue")
    private BigDecimal orgRevenue;

    @Column("freelancer_org_id")
    private String freelancerOrgId;

    @Column("sub_deliverer_commission")
    private BigDecimal subDelivererCommission;

    @Column("sub_deliverer_id")
    private String subDelivererId;

    @Column("status")
    @Builder.Default
    private String status = "PENDING";

    @Column("executed_at")
    private LocalDateTime executedAt;

    @Column("created_at")
    private LocalDateTime createdAt;
}
