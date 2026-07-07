package com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity for BillingKPISnapshot persistence.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_billing_kpi_snapshots")
public class BillingKPISnapshotEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("tenant_id")             private UUID tenantId;
    @Column("open_invoices_count")   private long openInvoicesCount;
    @Column("overdue_invoices_count") private long overdueInvoicesCount;
    @Column("paid_invoices_today")   private long paidInvoicesToday;
    @Column("generated_invoices_today") private long generatedInvoicesToday;
    @Column("outstanding_amount")    private BigDecimal outstandingAmount;
    @Column("collected_today")       private BigDecimal collectedToday;
    @Column("generated_today")       private BigDecimal generatedToday;
    @Column("currency")              private String currency;
    @Column("day_collection_rate")   private double dayCollectionRate;
    @Column("mtd_collection_rate")   private double mtdCollectionRate;
    @Column("avg_invoice_value")     private BigDecimal avgInvoiceValue;
    @Column("avg_days_to_pay")       private long avgDaysToPay;
    @Column("snapshot_at")           private Instant snapshotAt;

    public BillingKPISnapshotEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public long getOpenInvoicesCount() { return openInvoicesCount; }
    public void setOpenInvoicesCount(long v) { this.openInvoicesCount = v; }
    public long getOverdueInvoicesCount() { return overdueInvoicesCount; }
    public void setOverdueInvoicesCount(long v) { this.overdueInvoicesCount = v; }
    public long getPaidInvoicesToday() { return paidInvoicesToday; }
    public void setPaidInvoicesToday(long v) { this.paidInvoicesToday = v; }
    public long getGeneratedInvoicesToday() { return generatedInvoicesToday; }
    public void setGeneratedInvoicesToday(long v) { this.generatedInvoicesToday = v; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public void setOutstandingAmount(BigDecimal v) { this.outstandingAmount = v; }
    public BigDecimal getCollectedToday() { return collectedToday; }
    public void setCollectedToday(BigDecimal v) { this.collectedToday = v; }
    public BigDecimal getGeneratedToday() { return generatedToday; }
    public void setGeneratedToday(BigDecimal v) { this.generatedToday = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public double getDayCollectionRate() { return dayCollectionRate; }
    public void setDayCollectionRate(double v) { this.dayCollectionRate = v; }
    public double getMtdCollectionRate() { return mtdCollectionRate; }
    public void setMtdCollectionRate(double v) { this.mtdCollectionRate = v; }
    public BigDecimal getAvgInvoiceValue() { return avgInvoiceValue; }
    public void setAvgInvoiceValue(BigDecimal v) { this.avgInvoiceValue = v; }
    public long getAvgDaysToPay() { return avgDaysToPay; }
    public void setAvgDaysToPay(long v) { this.avgDaysToPay = v; }
    public Instant getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(Instant snapshotAt) { this.snapshotAt = snapshotAt; }
}
