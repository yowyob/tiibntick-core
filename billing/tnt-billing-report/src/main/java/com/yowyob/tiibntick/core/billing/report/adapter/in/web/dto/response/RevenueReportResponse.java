package com.yowyob.tiibntick.core.billing.report.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;
import com.yowyob.tiibntick.core.billing.report.domain.model.RevenueReport.CountryRevenueBreakdown;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response for RevenueReport.
 *
 * @author MANFOUO Braun
 */
public record RevenueReportResponse(
        UUID id,
        UUID tenantId,
        ReportPeriod period,
        long totalInvoicesGenerated,
        long totalInvoicesPaid,
        long totalInvoicesCancelled,
        long totalInvoicesOverdue,
        Money grossRevenue,
        Money collectedRevenue,
        Money cancelledRevenue,
        Money overdueRevenue,
        Money totalTaxCollected,
        Money netRevenue,
        double collectionRatePercent,
        List<CountryRevenueBreakdown> countryBreakdowns,
        Instant generatedAt
) {}
