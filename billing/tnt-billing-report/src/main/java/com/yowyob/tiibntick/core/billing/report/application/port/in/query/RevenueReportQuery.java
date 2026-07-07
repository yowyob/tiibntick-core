package com.yowyob.tiibntick.core.billing.report.application.port.in.query;

import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;

import java.util.UUID;

/**
 * Query to generate a RevenueReport for a given tenant and period.
 *
 * @author MANFOUO Braun
 */
public record RevenueReportQuery(
        UUID tenantId,
        ReportPeriod period,
        String currency
) {
    public RevenueReportQuery {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (period == null) throw new IllegalArgumentException("period is required");
        if (currency == null) currency = "XAF";
    }
}
