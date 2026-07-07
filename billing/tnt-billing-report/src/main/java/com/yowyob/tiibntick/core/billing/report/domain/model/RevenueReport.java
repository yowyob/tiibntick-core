package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model: RevenueReport.
 *
 * <p>Aggregates revenue metrics for a tenant over a given period:
 * total invoiced amount, collected revenue, tax collected, cancelled revenue,
 * and a breakdown per country code (multi-country support).</p>
 *
 * @author MANFOUO Braun
 */
public final class RevenueReport {

    private final UUID id;
    private final UUID tenantId;
    private final ReportPeriod period;

    private final long totalInvoicesGenerated;
    private final long totalInvoicesPaid;
    private final long totalInvoicesCancelled;
    private final long totalInvoicesOverdue;

    private final Money grossRevenue;
    private final Money collectedRevenue;
    private final Money cancelledRevenue;
    private final Money overdueRevenue;
    private final Money totalTaxCollected;
    private final Money netRevenue;

    /** Revenue breakdown by country code. */
    private final List<CountryRevenueBreakdown> countryBreakdowns;

    private final Instant generatedAt;

    private RevenueReport(
            UUID id, UUID tenantId, ReportPeriod period,
            long totalInvoicesGenerated, long totalInvoicesPaid,
            long totalInvoicesCancelled, long totalInvoicesOverdue,
            Money grossRevenue, Money collectedRevenue,
            Money cancelledRevenue, Money overdueRevenue,
            Money totalTaxCollected, Money netRevenue,
            List<CountryRevenueBreakdown> countryBreakdowns,
            Instant generatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.period = Objects.requireNonNull(period, "period is required");
        this.totalInvoicesGenerated = totalInvoicesGenerated;
        this.totalInvoicesPaid = totalInvoicesPaid;
        this.totalInvoicesCancelled = totalInvoicesCancelled;
        this.totalInvoicesOverdue = totalInvoicesOverdue;
        this.grossRevenue = grossRevenue;
        this.collectedRevenue = collectedRevenue;
        this.cancelledRevenue = cancelledRevenue;
        this.overdueRevenue = overdueRevenue;
        this.totalTaxCollected = totalTaxCollected;
        this.netRevenue = netRevenue;
        this.countryBreakdowns = countryBreakdowns != null ? List.copyOf(countryBreakdowns) : List.of();
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt is required");
    }

    public static RevenueReport of(
            UUID tenantId, ReportPeriod period,
            long generated, long paid, long cancelled, long overdue,
            Money gross, Money collected, Money cancelledAmt,
            Money overdueAmt, Money tax, Money net,
            List<CountryRevenueBreakdown> breakdowns) {
        return new RevenueReport(
                UUID.randomUUID(), tenantId, period,
                generated, paid, cancelled, overdue,
                gross, collected, cancelledAmt, overdueAmt, tax, net,
                breakdowns, Instant.now());
    }

    /** Collection rate as a percentage: collectedRevenue / grossRevenue * 100 */
    public double collectionRatePercent() {
        if (grossRevenue.isZero()) return 0.0;
        return collectedRevenue.amount()
                .divide(grossRevenue.amount(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal.valueOf(100))
                .doubleValue();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public ReportPeriod getPeriod() { return period; }
    public long getTotalInvoicesGenerated() { return totalInvoicesGenerated; }
    public long getTotalInvoicesPaid() { return totalInvoicesPaid; }
    public long getTotalInvoicesCancelled() { return totalInvoicesCancelled; }
    public long getTotalInvoicesOverdue() { return totalInvoicesOverdue; }
    public Money getGrossRevenue() { return grossRevenue; }
    public Money getCollectedRevenue() { return collectedRevenue; }
    public Money getCancelledRevenue() { return cancelledRevenue; }
    public Money getOverdueRevenue() { return overdueRevenue; }
    public Money getTotalTaxCollected() { return totalTaxCollected; }
    public Money getNetRevenue() { return netRevenue; }
    public List<CountryRevenueBreakdown> getCountryBreakdowns() { return countryBreakdowns; }
    public Instant getGeneratedAt() { return generatedAt; }

    /** Inner record: per-country revenue breakdown. */
    public record CountryRevenueBreakdown(
            String countryCode,
            long invoiceCount,
            Money revenue,
            Money taxAmount
    ) {}
}
