package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model: MarginReport.
 *
 * <p>Analyzes gross and net margins per period and per logistics service type.
 * Margin = Revenue − Costs (operational costs, deliverer payouts, platform overhead).</p>
 *
 * @author MANFOUO Braun
 */
public final class MarginReport {

    private final UUID id;
    private final UUID tenantId;
    private final ReportPeriod period;

    private final Money totalRevenue;
    private final Money totalCosts;
    private final Money grossMargin;
    private final double grossMarginPercent;
    private final Money totalPlatformFees;
    private final Money netMargin;
    private final double netMarginPercent;

    /** Per-service-type margin breakdown. */
    private final List<ServiceMargin> serviceBreakdowns;

    private final Instant generatedAt;

    private MarginReport(
            UUID id, UUID tenantId, ReportPeriod period,
            Money totalRevenue, Money totalCosts, Money grossMargin,
            double grossMarginPercent, Money totalPlatformFees,
            Money netMargin, double netMarginPercent,
            List<ServiceMargin> serviceBreakdowns,
            Instant generatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.period = Objects.requireNonNull(period);
        this.totalRevenue = Objects.requireNonNull(totalRevenue);
        this.totalCosts = Objects.requireNonNull(totalCosts);
        this.grossMargin = Objects.requireNonNull(grossMargin);
        this.grossMarginPercent = grossMarginPercent;
        this.totalPlatformFees = Objects.requireNonNull(totalPlatformFees);
        this.netMargin = Objects.requireNonNull(netMargin);
        this.netMarginPercent = netMarginPercent;
        this.serviceBreakdowns = serviceBreakdowns != null ? List.copyOf(serviceBreakdowns) : List.of();
        this.generatedAt = Objects.requireNonNull(generatedAt);
    }

    public static MarginReport of(
            UUID tenantId, ReportPeriod period,
            Money revenue, Money costs, Money platformFees,
            List<ServiceMargin> breakdowns) {

        Money gross = revenue.subtract(costs);
        double grossPct = revenue.isZero() ? 0.0
                : gross.amount().divide(revenue.amount(), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(java.math.BigDecimal.valueOf(100)).doubleValue();
        Money net = gross.subtract(platformFees);
        double netPct = revenue.isZero() ? 0.0
                : net.amount().divide(revenue.amount(), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(java.math.BigDecimal.valueOf(100)).doubleValue();

        return new MarginReport(UUID.randomUUID(), tenantId, period,
                revenue, costs, gross, grossPct, platformFees, net, netPct,
                breakdowns, Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public ReportPeriod getPeriod() { return period; }
    public Money getTotalRevenue() { return totalRevenue; }
    public Money getTotalCosts() { return totalCosts; }
    public Money getGrossMargin() { return grossMargin; }
    public double getGrossMarginPercent() { return grossMarginPercent; }
    public Money getTotalPlatformFees() { return totalPlatformFees; }
    public Money getNetMargin() { return netMargin; }
    public double getNetMarginPercent() { return netMarginPercent; }
    public List<ServiceMargin> getServiceBreakdowns() { return serviceBreakdowns; }
    public Instant getGeneratedAt() { return generatedAt; }

    /** Per-service-type margin breakdown. */
    public record ServiceMargin(
            String serviceType,
            long deliveryCount,
            Money revenue,
            Money cost,
            Money margin,
            double marginPercent
    ) {}
}
