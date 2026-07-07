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
 * R2DBC entity mapped to the wallet_transactions table.
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "billing", value = "wallet_transactions")
public class WalletTransactionEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("wallet_id")
    private UUID walletId;

    @Column("type")
    private String type;

    @Column("amount")
    private BigDecimal amount;

    @Column("balance_after")
    private BigDecimal balanceAfter;

    @Column("currency")
    private String currency;

    @Column("channel")
    private String channel;

    @Column("reference_id")
    private String referenceId;

    @Column("external_ref")
    private String externalRef;

    @Column("status")
    private String status;

    @Column("description")
    private String description;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("failure_reason")
    private String failureReason;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("processed_at")
    private LocalDateTime processedAt;
}
