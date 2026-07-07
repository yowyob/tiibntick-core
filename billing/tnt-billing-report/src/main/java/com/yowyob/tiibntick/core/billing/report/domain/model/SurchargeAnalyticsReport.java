package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain model: SurchargeAnalyticsReport.
 *
 * <p>Provides analytics on billing DSL surcharges applied during a period
 * for a specific actor's billing policy. Helps FreelancerOrg OWNERs and
 * platform admins understand which surcharge rules are most frequently triggered.</p>
 *
 * <p> — New report type supporting the extended Billing DSL.</p>
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class SurchargeAnalyticsReport {

    private final UUID id;
    private final UUID tenantId;
    private final String ownerOrgId;
    private final String ownerOrgType;  // AGENCY | FREELANCER_ORG | POINT | LINK | ADMIN
    private final ReportPeriod period;

    private final long totalSurchargesTriggered;
    private final long totalDeliveriesWithSurcharge;
    private final Money totalSurchargeRevenue;
    private final double averageSurchargePerDelivery;

    /** Per-surcharge-code breakdown. */
    private final List<SurchargeBreakdown> surchargeBreakdowns;

    private final Instant generatedAt;

    /**
     * Breakdown for a single surcharge code.
     */
    public record SurchargeBreakdown(
            String surchargeCode,
            String labelFr,
            String labelEn,
            long timesTriggered,
            Money totalAmount,
            double triggerRate  // percentage of deliveries that triggered this surcharge
    ) {}
}
