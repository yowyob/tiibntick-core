package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain model: FreelancerOrgReport.
 *
 * <p>Aggregates financial metrics for a specific FreelancerOrganization over a period.
 * Provides revenue, commission split, and sub-deliverer performance data for the
 * FreelancerOrg OWNER dashboard and platform admin reporting.</p>
 *
 * <p> — New report type introduced with the FreelancerOrganization model.</p>
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class FreelancerOrgReport {

    private final UUID id;
    private final UUID tenantId;

    /**
     * UUID of the FreelancerOrg this report covers.
     * References tnt-organization-core UUID — pure integration key.
     */
    private final String freelancerOrgId;

    private final String tradeName;
    private final ReportPeriod period;

    // ── Revenue metrics ───────────────────────────────────────────────────────

    private final long totalDeliveries;
    private final long completedDeliveries;
    private final long cancelledDeliveries;
    private final long failedDeliveries;

    /** Total amount invoiced by the FreelancerOrg for this period (XAF). */
    private final Money totalRevenue;

    /** Platform commission deducted from total revenue. */
    private final Money platformCommission;

    /** Net revenue after platform commission deduction. */
    private final Money netRevenue;

    /** Total amounts paid to sub-deliverers as commission (if any). */
    private final Money subDelivererCommissionPaid;

    /** Final org revenue after all deductions. */
    private final Money orgFinalRevenue;

    // ── Sub-deliverer breakdown ───────────────────────────────────────────────

    /** Per-sub-deliverer mission and commission breakdown. */
    private final List<SubDelivererPerformance> subDelivererBreakdowns;

    // ── Applied templates and surcharges ─────────────────────────────────────

    /** Number of distinct billing policy templates applied during this period. */
    private final long templatesApplied;

    /** Total surcharges collected (FRAGILE, NIGHT, REFRIGERATED, etc.). */
    private final Money totalSurchargesCollected;

    private final Instant generatedAt;

    /**
     * Per-sub-deliverer performance data for a given period.
     */
    public record SubDelivererPerformance(
            String subDelivererId,
            long deliveries,
            Money commissionEarned
    ) {}
}
