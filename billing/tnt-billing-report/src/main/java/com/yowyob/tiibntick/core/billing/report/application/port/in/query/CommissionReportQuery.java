package com.yowyob.tiibntick.core.billing.report.application.port.in.query;

import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;

import java.util.UUID;

/**
 * Query to generate a CommissionSummary for a given tenant and period.
 *
 * @author MANFOUO Braun
 */
public record CommissionReportQuery(
        UUID tenantId,
        ReportPeriod period,
        String currency
) {}
