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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "billing", value = "wallet_settlements")
public class WalletSettlementEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id") private UUID tenantId;
    @Column("reference_type") private String referenceType;
    @Column("reference_id") private UUID referenceId;
    @Column("payer_user_id") private UUID payerUserId;
    @Column("payee_user_id") private UUID payeeUserId;
    @Column("amount") private BigDecimal amount;
    @Column("currency") private String currency;
    @Column("method") private String method;
    @Column("status") private String status;
    @Column("kernel_reference") private String kernelReference;
    @Column("commission_rate") private BigDecimal commissionRate;
    @Column("commission_amount") private BigDecimal commissionAmount;
    @Column("commission_status") private String commissionStatus;
    @Column("created_at") private LocalDateTime createdAt;
    @Column("paid_at") private LocalDateTime paidAt;
    @Column("updated_at") private LocalDateTime updatedAt;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
