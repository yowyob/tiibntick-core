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
 * R2DBC entity mapped to the wallet_payment_intents table.
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "billing", value = "wallet_payment_intents")
public class PaymentIntentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("wallet_id")
    private UUID walletId;

    @Column("invoice_id")
    private String invoiceId;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("channel")
    private String channel;

    @Column("status")
    private String status;

    @Column("external_ref")
    private String externalRef;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("callback_url")
    private String callbackUrl;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
