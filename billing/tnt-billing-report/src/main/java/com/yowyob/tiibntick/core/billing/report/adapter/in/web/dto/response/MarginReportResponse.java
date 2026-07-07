package com.yowyob.tiibntick.core.billing.report.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.report.domain.model.MarginReport.ServiceMargin;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response for MarginReport.
 *
 * @author MANFOUO Braun
 */
public record MarginReportResponse(
        UUID id,
        UUID tenantId,
        ReportPeriod period,
        Money totalRevenue,
        Money totalCosts,
        Money grossMargin,
        double grossMarginPercent,
        Money totalPlatformFees,
        Money netMargin,
        double netMarginPercent,
        List<ServiceMargin> serviceBreakdowns,
        Instant generatedAt
) {}
