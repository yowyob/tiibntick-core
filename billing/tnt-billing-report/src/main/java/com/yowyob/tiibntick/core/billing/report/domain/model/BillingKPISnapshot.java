package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model: BillingKPISnapshot.
 *
 * <p>A point-in-time snapshot of key billing KPIs for a tenant.
 * Used for dashboard display and monitoring alerts.
 * Snapshots are created periodically (daily/hourly) and persisted for trend analysis.</p>
 *
 * @author MANFOUO Braun
 */
public final class BillingKPISnapshot {

    private final UUID id;
    private final UUID tenantId;

    private final long openInvoicesCount;
    private final long overdueInvoicesCount;
    private final long paidInvoicesToday;
    private final long generatedInvoicesToday;

    private final Money outstandingAmount;
    private final Money collectedToday;
    private final Money generatedToday;

    private final double dayCollectionRate;
    private final double monthToDateCollectionRate;

    private final Money averageInvoiceValue;
    private final long averageDaysToPay;

    private final Instant snapshotAt;

    private BillingKPISnapshot(
            UUID id, UUID tenantId,
            long openInvoicesCount, long overdueInvoicesCount,
            long paidInvoicesToday, long generatedInvoicesToday,
            Money outstandingAmount, Money collectedToday, Money generatedToday,
            double dayCollectionRate, double monthToDateCollectionRate,
            Money averageInvoiceValue, long averageDaysToPay,
            Instant snapshotAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.openInvoicesCount = openInvoicesCount;
        this.overdueInvoicesCount = overdueInvoicesCount;
        this.paidInvoicesToday = paidInvoicesToday;
        this.generatedInvoicesToday = generatedInvoicesToday;
        this.outstandingAmount = Objects.requireNonNull(outstandingAmount);
        this.collectedToday = Objects.requireNonNull(collectedToday);
        this.generatedToday = Objects.requireNonNull(generatedToday);
        this.dayCollectionRate = dayCollectionRate;
        this.monthToDateCollectionRate = monthToDateCollectionRate;
        this.averageInvoiceValue = Objects.requireNonNull(averageInvoiceValue);
        this.averageDaysToPay = averageDaysToPay;
        this.snapshotAt = Objects.requireNonNull(snapshotAt);
    }

    public static BillingKPISnapshot take(
            UUID tenantId,
            long openCount, long overdueCount,
            long paidToday, long generatedToday,
            Money outstanding, Money collectedToday, Money generatedTodayAmt,
            double dayRate, double mtdRate,
            Money avgInvoiceValue, long avgDaysToPay) {
        return new BillingKPISnapshot(
                UUID.randomUUID(), tenantId,
                openCount, overdueCount, paidToday, generatedToday,
                outstanding, collectedToday, generatedTodayAmt,
                dayRate, mtdRate, avgInvoiceValue, avgDaysToPay,
                Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public long getOpenInvoicesCount() { return openInvoicesCount; }
    public long getOverdueInvoicesCount() { return overdueInvoicesCount; }
    public long getPaidInvoicesToday() { return paidInvoicesToday; }
    public long getGeneratedInvoicesToday() { return generatedInvoicesToday; }
    public Money getOutstandingAmount() { return outstandingAmount; }
    public Money getCollectedToday() { return collectedToday; }
    public Money getGeneratedToday() { return generatedToday; }
    public double getDayCollectionRate() { return dayCollectionRate; }
    public double getMonthToDateCollectionRate() { return monthToDateCollectionRate; }
    public Money getAverageInvoiceValue() { return averageInvoiceValue; }
    public long getAverageDaysToPay() { return averageDaysToPay; }
    public Instant getSnapshotAt() { return snapshotAt; }
}
