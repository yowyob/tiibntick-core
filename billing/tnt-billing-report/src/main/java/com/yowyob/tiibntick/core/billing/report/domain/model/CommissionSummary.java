package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model: CommissionSummary.
 *
 * <p>Aggregates platform commission metrics for a tenant period.
 * Commission = platform fee charged on each delivery invoice.
 * Provides per-deliverer and per-agency breakdowns.</p>
 *
 * @author MANFOUO Braun
 */
public final class CommissionSummary {

    private final UUID id;
    private final UUID tenantId;
    private final ReportPeriod period;

    private final long totalDeliveries;
    private final Money totalCommissionsEarned;
    private final Money totalCommissionsPaid;
    private final Money totalCommissionsPending;
    private final double averageCommissionRate;

    /** Per-actor commission breakdown. */
    private final List<ActorCommission> actorBreakdowns;

    private final Instant generatedAt;

    private CommissionSummary(
            UUID id, UUID tenantId, ReportPeriod period,
            long totalDeliveries,
            Money totalCommissionsEarned, Money totalCommissionsPaid,
            Money totalCommissionsPending, double averageCommissionRate,
            List<ActorCommission> actorBreakdowns,
            Instant generatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.period = Objects.requireNonNull(period, "period is required");
        this.totalDeliveries = totalDeliveries;
        this.totalCommissionsEarned = Objects.requireNonNull(totalCommissionsEarned);
        this.totalCommissionsPaid = Objects.requireNonNull(totalCommissionsPaid);
        this.totalCommissionsPending = Objects.requireNonNull(totalCommissionsPending);
        this.averageCommissionRate = averageCommissionRate;
        this.actorBreakdowns = actorBreakdowns != null ? List.copyOf(actorBreakdowns) : List.of();
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt is required");
    }

    public static CommissionSummary of(
            UUID tenantId, ReportPeriod period,
            long totalDeliveries,
            Money earned, Money paid, Money pending,
            double avgRate, List<ActorCommission> breakdowns) {
        return new CommissionSummary(
                UUID.randomUUID(), tenantId, period,
                totalDeliveries, earned, paid, pending, avgRate,
                breakdowns, Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public ReportPeriod getPeriod() { return period; }
    public long getTotalDeliveries() { return totalDeliveries; }
    public Money getTotalCommissionsEarned() { return totalCommissionsEarned; }
    public Money getTotalCommissionsPaid() { return totalCommissionsPaid; }
    public Money getTotalCommissionsPending() { return totalCommissionsPending; }
    public double getAverageCommissionRate() { return averageCommissionRate; }
    public List<ActorCommission> getActorBreakdowns() { return actorBreakdowns; }
    public Instant getGeneratedAt() { return generatedAt; }

    /**
     * Per-actor commission breakdown record.
     */
    public record ActorCommission(
            String actorId,
            String actorName,
            String actorType,
            long deliveryCount,
            Money commissionAmount,
            double commissionRate
    ) {}
}
