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
 * R2DBC entity mapped to the {@code billing.wallet_reconciliations} table.
 *
 * <p>The table itself has existed since {@code 001-create-wallet-tables.yaml} — only the
 * R2DBC-backed adapter was missing, with {@code InMemoryReconciliationRepository} standing
 * in as a {@code ConcurrentHashMap} placeholder that lost every record on restart and
 * diverged across instances in a multi-instance deployment (Chantier D · Audit n°6 · S3).
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "billing", value = "wallet_reconciliations")
public class ReconciliationEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("period_year")
    private Integer periodYear;

    @Column("period_month")
    private Integer periodMonth;

    @Column("wallet_total")
    private BigDecimal walletTotal;

    @Column("bank_statement_total")
    private BigDecimal bankStatementTotal;

    @Column("discrepancy")
    private BigDecimal discrepancy;

    @Column("currency")
    private String currency;

    @Column("status")
    private String status;

    @Column("resolution_note")
    private String resolutionNote;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
