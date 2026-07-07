package com.yowyob.tiibntick.core.billing.report.application.port.out;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.report.domain.model.InvoiceReportEntry;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;
import com.yowyob.tiibntick.core.billing.report.domain.model.RevenueReport.CountryRevenueBreakdown;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: read-optimised queries over the invoice report projection table.
 *
 * @author MANFOUO Braun
 */
public interface InvoiceReportProjectionRepository {

    Mono<Void> upsert(InvoiceReportEntry entry);

    Mono<Long> countByTenantAndPeriodAndStatus(UUID tenantId, ReportPeriod period, InvoiceStatus status);

    Mono<Money> sumNetAmountByTenantAndPeriodAndStatus(UUID tenantId, ReportPeriod period, InvoiceStatus status, String currency);

    Mono<Money> sumTaxByTenantAndPeriod(UUID tenantId, ReportPeriod period, String currency);

    Flux<CountryRevenueBreakdown> revenueByCountry(UUID tenantId, ReportPeriod period, String currency);

    Flux<InvoiceReportEntry> findByTenantAndPeriod(UUID tenantId, ReportPeriod period);

    Mono<Double> averageNetAmount(UUID tenantId, ReportPeriod period, String currency);

    Mono<Long> averageDaysToPay(UUID tenantId, ReportPeriod period);

    Mono<Money> sumPlatformFeeByTenantAndPeriod(UUID tenantId, ReportPeriod period, String currency);
    /**
     * Sums total surcharge amounts grouped by surcharge-related data for analytics.
     * Used by SurchargeAnalyticsReport.
     */
    Mono<java.math.BigDecimal> sumTotalSurchargeByOwnerOrgAndPeriod(
            UUID tenantId,
            com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod period,
            String ownerOrgId, String currency);

    /**
     * Lists all invoices issued by a specific FreelancerOrg for a period.
     */
    reactor.core.publisher.Flux<com.yowyob.tiibntick.core.billing.report.domain.model.InvoiceReportEntry>
            findByFreelancerOrg(UUID tenantId,
                    com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod period,
                    String issuerOrgId);

    /**
     * Lists all invoices by applied template name.
     */
    reactor.core.publisher.Flux<com.yowyob.tiibntick.core.billing.report.domain.model.InvoiceReportEntry>
            findByAppliedTemplateName(UUID tenantId,
                    com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod period,
                    String templateName);

}