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

/** Row of {@code billing.wallet_compensations} (lot L5). Insert only. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "billing", value = "wallet_compensations")
public class WalletCompensationEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Column("tenant_id") private UUID tenantId;
    @Column("dispute_id") private String disputeId;
    @Column("kernel_compensation_id") private UUID kernelCompensationId;
    @Column("fund_wallet_id") private UUID fundWalletId;
    @Column("beneficiary_actor_id") private UUID beneficiaryActorId;
    @Column("beneficiary_wallet_id") private UUID beneficiaryWalletId;
    @Column("amount") private BigDecimal amount;
    @Column("currency") private String currency;
    @Column("created_at") private LocalDateTime createdAt;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
