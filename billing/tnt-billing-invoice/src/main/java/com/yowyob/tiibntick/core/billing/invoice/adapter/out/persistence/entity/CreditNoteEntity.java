package com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * R2DBC entity for CreditNote persistence.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_credit_notes")
public class CreditNoteEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("original_invoice_id") private UUID originalInvoiceId;
    @Column("tenant_id")           private UUID tenantId;
    @Column("amount_value")        private BigDecimal amountValue;
    @Column("amount_currency")     private String amountCurrency;
    @Column("reason")              private String reason;
    @Column("status")              private String status;
    @Column("issued_at")           private OffsetDateTime issuedAt;
    @Column("applied_at")          private OffsetDateTime appliedAt;

    public CreditNoteEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getOriginalInvoiceId() { return originalInvoiceId; }
    public void setOriginalInvoiceId(UUID originalInvoiceId) { this.originalInvoiceId = originalInvoiceId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public BigDecimal getAmountValue() { return amountValue; }
    public void setAmountValue(BigDecimal amountValue) { this.amountValue = amountValue; }
    public String getAmountCurrency() { return amountCurrency; }
    public void setAmountCurrency(String amountCurrency) { this.amountCurrency = amountCurrency; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(OffsetDateTime appliedAt) { this.appliedAt = appliedAt; }
}
