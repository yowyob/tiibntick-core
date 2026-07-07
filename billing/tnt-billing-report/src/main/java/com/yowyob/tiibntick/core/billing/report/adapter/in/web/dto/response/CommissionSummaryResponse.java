package com.yowyob.tiibntick.core.billing.report.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.report.domain.model.CommissionSummary.ActorCommission;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response for CommissionSummary.
 *
 * @author MANFOUO Braun
 */
public record CommissionSummaryResponse(
        UUID id,
        UUID tenantId,
        ReportPeriod period,
        long totalDeliveries,
        Money totalCommissionsEarned,
        Money totalCommissionsPaid,
        Money totalCommissionsPending,
        double averageCommissionRate,
        List<ActorCommission> actorBreakdowns,
        Instant generatedAt
) {}
