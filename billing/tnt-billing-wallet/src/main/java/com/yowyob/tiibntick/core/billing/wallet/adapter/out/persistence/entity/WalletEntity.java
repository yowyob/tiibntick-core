package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC entity mapped to the wallet_wallets table.
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "billing", value = "wallet_wallets")
public class WalletEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("user_id")
    private UUID userId;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("balance")
    private BigDecimal balance;

    @Column("reserved_balance")
    private BigDecimal reservedBalance;

    @Column("currency")
    private String currency;

    // : Multi-owner support
    /** Type of entity owning this wallet: ACTOR | FREELANCER_ORG | AGENCY */
    @Column("owner_type")
    @Builder.Default
    private String ownerType = "ACTOR";

    /**
     * UUID of the owner entity. For ACTOR = userId string.
     * For FREELANCER_ORG / AGENCY = org UUID string.
     */
    @Column("owner_id")
    private String ownerId;

    @Column("status")
    private String status;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private long version;
}
