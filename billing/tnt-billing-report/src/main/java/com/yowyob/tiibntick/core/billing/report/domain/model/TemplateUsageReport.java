package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain model: TemplateUsageReport.
 *
 * <p>Analytics on billing policy template usage by type and actor.
 * Used by platform admins to monitor which templates are most popular,
 * and to calibrate default parameter values.</p>
 *
 * <p> — New report type for the billing templates feature.</p>
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class TemplateUsageReport {

    private final UUID id;
    private final UUID tenantId;

    /**
     * Template code this report covers (e.g., "TPL-FRAGILE").
     * Null when reporting across all templates.
     */
    private final String templateCode;

    private final ReportPeriod period;

    private final long totalPoliciesCreated;
    private final long totalDeliveriesBilled;
    private final Money totalRevenue;
    private final double averageRevenuePerDelivery;

    /** Per-actor template usage breakdown. */
    private final List<ActorTemplateUsage> actorBreakdowns;

    private final Instant generatedAt;

    /**
     * Template usage data for a single actor.
     */
    public record ActorTemplateUsage(
            String actorId,
            String actorOrgType,    // AGENCY | FREELANCER_ORG | POINT | LINK
            long deliveriesBilled,
            Money revenueGenerated
    ) {}
}
