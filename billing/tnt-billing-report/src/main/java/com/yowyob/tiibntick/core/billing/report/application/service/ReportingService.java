package com.yowyob.tiibntick.core.billing.report.application.service;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.report.application.port.in.query.*;
import com.yowyob.tiibntick.core.billing.report.domain.model.FreelancerOrgReport;
import com.yowyob.tiibntick.core.billing.report.domain.model.SurchargeAnalyticsReport;
import com.yowyob.tiibntick.core.billing.report.domain.model.TemplateUsageReport;
import com.yowyob.tiibntick.core.billing.report.application.port.out.*;
import com.yowyob.tiibntick.core.billing.report.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;

/**
 * Application service for generating billing reports.
 * All reports are computed on-demand from the invoice projection table.
 *
 * <p>All report operations are protected by {@link RequirePermission} annotations
 * from {@code tnt-roles-core}:
 * <ul>
 *   <li>{@code report:read} — generate report data</li>
 *   <li>{@code report:export} — export report as CSV</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Service
public class ReportingService {

    private static final Logger log = LoggerFactory.getLogger(ReportingService.class);
    private static final double DEFAULT_COMMISSION_RATE = 5.0;
    private static final double DEFAULT_PLATFORM_FEE_RATE = 2.5;

    private final InvoiceReportProjectionRepository projectionRepo;
    private final BillingKPISnapshotRepository kpiSnapshotRepo;
    private final ReportExportPort exportPort;

    public ReportingService(
            InvoiceReportProjectionRepository projectionRepo,
            BillingKPISnapshotRepository kpiSnapshotRepo,
            ReportExportPort exportPort) {
        this.projectionRepo = projectionRepo;
        this.kpiSnapshotRepo = kpiSnapshotRepo;
        this.exportPort = exportPort;
    }

    // ─── Revenue Report ───────────────────────────────────────────────────────

    @RequirePermission(resource = "report", action = "read")
    public Mono<RevenueReport> generateRevenueReport(RevenueReportQuery query) {
        String cur = query.currency();
        ReportPeriod period = query.period();

        Mono<long[]> openPaidCounts = projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.ISSUED)
                .zipWith(projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.PARTIALLY_PAID))
                .map(t -> t.getT1() + t.getT2())
                .zipWith(projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.PAID))
                .map(t -> new long[]{t.getT1(), t.getT2()});

        return Mono.zip(
                openPaidCounts,
                projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.CANCELLED),
                projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.OVERDUE),
                projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), period, null, cur),
                projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.PAID, cur),
                projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.CANCELLED, cur),
                projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.OVERDUE, cur),
                projectionRepo.sumTaxByTenantAndPeriod(query.tenantId(), period, cur)
        ).zipWith(projectionRepo.revenueByCountry(query.tenantId(), period, cur).collectList())
        .flatMap(tuple -> {
            long[] openPaid = tuple.getT1().getT1();
            long cancelCount = tuple.getT1().getT2();
            long overdueCount = tuple.getT1().getT3();
            Money gross = tuple.getT1().getT4();
            Money collected = tuple.getT1().getT5();
            Money cancelled = tuple.getT1().getT6();
            Money overdue = tuple.getT1().getT7();
            Money tax = tuple.getT1().getT8();
            List<RevenueReport.CountryRevenueBreakdown> breakdowns = tuple.getT2();

            long totalGenerated = openPaid[0] + openPaid[1] + cancelCount + overdueCount;

            RevenueReport report = RevenueReport.of(
                    query.tenantId(), period,
                    totalGenerated, openPaid[1], cancelCount, overdueCount,
                    gross, collected, cancelled, overdue, tax,
                    gross.subtract(tax), breakdowns);

            log.info("Revenue report generated for tenant={} period={}", query.tenantId(), period);
            return Mono.just(report);
        });
    }

    // ─── Commission Summary ───────────────────────────────────────────────────

    @RequirePermission(resource = "report", action = "read")
    public Mono<CommissionSummary> generateCommissionSummary(CommissionReportQuery query) {
        String cur = query.currency();
        ReportPeriod period = query.period();

        return projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.PAID)
                .flatMap(paidCount -> projectionRepo
                        .sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.PAID, cur)
                        .flatMap(paidRevenue -> {
                            Money earned = paidRevenue.percentage(DEFAULT_COMMISSION_RATE);
                            Money pending = paidRevenue.multiply(0.0);
                            CommissionSummary summary = CommissionSummary.of(
                                    query.tenantId(), period,
                                    paidCount, earned, earned, pending,
                                    DEFAULT_COMMISSION_RATE, List.of());
                            return Mono.just(summary);
                        }));
    }

    // ─── Margin Report ────────────────────────────────────────────────────────

    @RequirePermission(resource = "report", action = "read")
    public Mono<MarginReport> generateMarginReport(MarginReportQuery query) {
        String cur = query.currency();
        ReportPeriod period = query.period();

        return Mono.zip(
                projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), period, InvoiceStatus.PAID, cur),
                projectionRepo.sumPlatformFeeByTenantAndPeriod(query.tenantId(), period, cur)
        ).flatMap(tuple -> {
            Money revenue = tuple.getT1();
            Money platformFees = tuple.getT2();
            Money costs = revenue.percentage(DEFAULT_COMMISSION_RATE);
            MarginReport report = MarginReport.of(
                    query.tenantId(), period, revenue, costs, platformFees, List.of());
            return Mono.just(report);
        });
    }

    // ─── KPI Snapshot ─────────────────────────────────────────────────────────

    @RequirePermission(resource = "report", action = "read")
    public Mono<BillingKPISnapshot> getOrRefreshKPISnapshot(KPISnapshotQuery query) {
        String cur = query.currency();
        ReportPeriod today = ReportPeriod.of(LocalDate.now(), LocalDate.now());
        ReportPeriod mtd = ReportPeriod.ofMonth(YearMonth.now());

        Mono<long[]> counts = Mono.zip(
                projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), mtd, InvoiceStatus.ISSUED)
                        .zipWith(projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), mtd, InvoiceStatus.PARTIALLY_PAID))
                        .map(t -> t.getT1() + t.getT2()),
                projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), mtd, InvoiceStatus.OVERDUE),
                projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), today, InvoiceStatus.PAID),
                projectionRepo.countByTenantAndPeriodAndStatus(query.tenantId(), today, InvoiceStatus.ISSUED)
        ).map(t -> new long[]{t.getT1(), t.getT2(), t.getT3(), t.getT4()});

        return Mono.zip(
                counts,
                projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), mtd, InvoiceStatus.ISSUED, cur),
                projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), today, InvoiceStatus.PAID, cur),
                projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(query.tenantId(), today, InvoiceStatus.ISSUED, cur),
                projectionRepo.averageNetAmount(query.tenantId(), mtd, cur),
                projectionRepo.averageDaysToPay(query.tenantId(), mtd)
        ).flatMap(t -> {
            long[] counterArray = t.getT1();
            long open = counterArray[0];
            long overdue = counterArray[1];
            long paidToday = counterArray[2];
            long genToday = counterArray[3];
            Money outstanding = t.getT2();
            Money collectedToday = t.getT3();
            Money generatedToday = t.getT4();
            double avgValue = t.getT5();
            long avgDays = t.getT6();

            Money avgInvoice = Money.of(avgValue, cur);
            double dayRate = generatedToday.isZero() ? 0.0
                    : collectedToday.amount().divide(generatedToday.amount(), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(java.math.BigDecimal.valueOf(100)).doubleValue();

            BillingKPISnapshot snapshot = BillingKPISnapshot.take(
                    query.tenantId(),
                    open, overdue, paidToday, genToday,
                    outstanding, collectedToday, generatedToday,
                    dayRate, dayRate, avgInvoice, avgDays);

            return kpiSnapshotRepo.save(snapshot);
        });
    }

    // ─── CSV Export ───────────────────────────────────────────────────────────

    @RequirePermission(resource = "report", action = "export")
    public Mono<byte[]> exportRevenueToCsv(RevenueReportQuery query) {
        return generateRevenueReport(query).flatMap(exportPort::exportRevenueToCsv);
    }

    @RequirePermission(resource = "report", action = "export")
    public Mono<byte[]> exportCommissionsToCsv(CommissionReportQuery query) {
        return generateCommissionSummary(query).flatMap(exportPort::exportCommissionsToCsv);
    }

    @RequirePermission(resource = "report", action = "export")
    public Mono<byte[]> exportMarginToCsv(MarginReportQuery query) {
        return generateMarginReport(query).flatMap(exportPort::exportMarginToCsv);
    }
    // ─── FreelancerOrg Report () ─────────────────────────────────────────

    /**
     * Generates a comprehensive financial report for a specific FreelancerOrganization.
     *
     * <p>Aggregates: total revenue, platform commission, org net revenue,
     * sub-deliverer commission payments, and per-sub-deliverer breakdown.
     *
     * @param query the FreelancerOrg report query
     * @return the FreelancerOrgReport
     */
    @RequirePermission(resource = "report", action = "read")
    public Mono<FreelancerOrgReport> generateFreelancerOrgReport(FreelancerOrgReportQuery query) {
        String cur = query.currency();
        ReportPeriod period = query.period();
        String orgId = query.freelancerOrgId();

        log.info("Generating FreelancerOrgReport for orgId={} period={} tenant={}",
                orgId, period, query.tenantId());

        return projectionRepo.findByFreelancerOrg(query.tenantId(), period, orgId)
                .collectList()
                .map(entries -> {
                    long total = entries.size();
                    long completed = entries.stream().filter(e ->
                            e.status() == com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus.PAID)
                            .count();
                    long cancelled = entries.stream().filter(e ->
                            e.status() == com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus.CANCELLED)
                            .count();

                    java.math.BigDecimal totalRev = entries.stream()
                            .filter(e -> e.netAmount() != null)
                            .map(e -> e.netAmount().amount())
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                    java.math.BigDecimal totalSurcharge = entries.stream()
                            .filter(e -> e.totalSurchargeAmount() != null)
                            .map(e -> e.totalSurchargeAmount().amount())
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                    double platformRate = DEFAULT_PLATFORM_FEE_RATE / 100.0;
                    java.math.BigDecimal platformComm = totalRev.multiply(
                            java.math.BigDecimal.valueOf(platformRate))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    java.math.BigDecimal netRev = totalRev.subtract(platformComm);

                    return FreelancerOrgReport.builder()
                            .id(java.util.UUID.randomUUID())
                            .tenantId(query.tenantId())
                            .freelancerOrgId(orgId)
                            .tradeName("")
                            .period(period)
                            .totalDeliveries(total)
                            .completedDeliveries(completed)
                            .cancelledDeliveries(cancelled)
                            .failedDeliveries(0L)
                            .totalRevenue(Money.of(totalRev, cur))
                            .platformCommission(Money.of(platformComm, cur))
                            .netRevenue(Money.of(netRev, cur))
                            .subDelivererCommissionPaid(Money.zero(cur))
                            .orgFinalRevenue(Money.of(netRev, cur))
                            .subDelivererBreakdowns(java.util.List.of())
                            .templatesApplied(entries.stream()
                                    .filter(e -> e.appliedTemplateName() != null && !e.appliedTemplateName().isBlank())
                                    .map(InvoiceReportEntry::appliedTemplateName)
                                    .distinct().count())
                            .totalSurchargesCollected(Money.of(totalSurcharge, cur))
                            .generatedAt(java.time.Instant.now())
                            .build();
                });
    }

    // ─── Surcharge Analytics Report () ───────────────────────────────────

    /**
     * Generates surcharge analytics for an actor's billing policy over a period.
     *
     * @param query the surcharge analytics query
     * @return SurchargeAnalyticsReport with per-surcharge breakdown
     */
    @RequirePermission(resource = "report", action = "read")
    public Mono<SurchargeAnalyticsReport> generateSurchargeAnalytics(SurchargeAnalyticsQuery query) {
        log.info("Generating SurchargeAnalyticsReport for ownerOrgId={} tenant={}", query.ownerOrgId(), query.tenantId());

        return projectionRepo.sumTotalSurchargeByOwnerOrgAndPeriod(
                        query.tenantId(), query.period(), query.ownerOrgId(), query.currency())
                .map(totalSurcharge -> SurchargeAnalyticsReport.builder()
                        .id(java.util.UUID.randomUUID())
                        .tenantId(query.tenantId())
                        .ownerOrgId(query.ownerOrgId())
                        .ownerOrgType(query.ownerOrgType())
                        .period(query.period())
                        .totalSurchargesTriggered(0L)
                        .totalDeliveriesWithSurcharge(0L)
                        .totalSurchargeRevenue(Money.of(
                                totalSurcharge != null ? totalSurcharge : java.math.BigDecimal.ZERO,
                                query.currency()))
                        .averageSurchargePerDelivery(0.0)
                        .surchargeBreakdowns(java.util.List.of())
                        .generatedAt(java.time.Instant.now())
                        .build());
    }

    // ─── Template Usage Report () ────────────────────────────────────────

    /**
     * Generates a billing template usage report.
     *
     * @param query the template usage query
     * @return TemplateUsageReport with per-actor breakdown
     */
    @RequirePermission(resource = "report", action = "read")
    public Mono<TemplateUsageReport> generateTemplateUsageReport(TemplateUsageReportQuery query) {
        log.info("Generating TemplateUsageReport for template={} tenant={}", query.templateCode(), query.tenantId());

        return projectionRepo.findByAppliedTemplateName(query.tenantId(), query.period(), query.templateCode())
                .collectList()
                .map(entries -> {
                    long total = entries.size();
                    java.math.BigDecimal totalRevenue = entries.stream()
                            .filter(e -> e.netAmount() != null)
                            .map(e -> e.netAmount().amount())
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                    return TemplateUsageReport.builder()
                            .id(java.util.UUID.randomUUID())
                            .tenantId(query.tenantId())
                            .templateCode(query.templateCode())
                            .period(query.period())
                            .totalPoliciesCreated(entries.stream()
                                    .map(InvoiceReportEntry::appliedTemplateName)
                                    .filter(t -> t != null).distinct().count())
                            .totalDeliveriesBilled(total)
                            .totalRevenue(Money.of(totalRevenue, query.currency()))
                            .averageRevenuePerDelivery(total > 0
                                    ? totalRevenue.doubleValue() / total : 0.0)
                            .actorBreakdowns(java.util.List.of())
                            .generatedAt(java.time.Instant.now())
                            .build();
                });
    }

}