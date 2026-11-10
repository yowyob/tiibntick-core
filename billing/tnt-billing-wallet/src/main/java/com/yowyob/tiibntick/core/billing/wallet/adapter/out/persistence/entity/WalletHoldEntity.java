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
@Table(schema = "billing", value = "wallet_holds")
public class WalletHoldEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id") private UUID tenantId;
    @Column("owner_user_id") private UUID ownerUserId;
    @Column("reference_type") private String referenceType;
    @Column("reference_id") private UUID referenceId;
    @Column("amount") private BigDecimal amount;
    @Column("currency") private String currency;
    @Column("reason") private String reason;
    @Column("status") private String status;
    @Column("created_at") private LocalDateTime createdAt;
    @Column("released_at") private LocalDateTime releasedAt;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
