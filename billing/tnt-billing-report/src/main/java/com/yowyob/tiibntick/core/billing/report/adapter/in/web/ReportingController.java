package com.yowyob.tiibntick.core.billing.report.adapter.in.web;

import com.yowyob.tiibntick.core.billing.report.adapter.in.web.dto.response.*;
import com.yowyob.tiibntick.core.billing.report.adapter.in.web.mapper.ReportWebMapper;
import com.yowyob.tiibntick.core.billing.report.application.port.in.query.*;
import com.yowyob.tiibntick.core.billing.report.application.service.ReportingService;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for billing reports.
 * Base path: /api/v1/billing/reports
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/billing/reports")
@Tag(name = "TiiBnTick Billing Reports", description = "Revenue, Commission, Margin reports and KPI snapshots with CSV export")
public class ReportingController {

    private final ReportingService reportingService;
    private final ReportWebMapper mapper;

    public ReportingController(ReportingService reportingService, ReportWebMapper mapper) {
        this.reportingService = reportingService;
        this.mapper = mapper;
    }

    // ─── Revenue ─────────────────────────────────────────────────────────────

    @GetMapping("/revenue")
    @Operation(summary = "Generate a revenue report for a given period")
    public Mono<RevenueReportResponse> getRevenueReport(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        RevenueReportQuery query = new RevenueReportQuery(tenantId, ReportPeriod.of(from, to), currency);
        return reportingService.generateRevenueReport(query).map(mapper::toResponse);
    }

    @GetMapping("/revenue/export/csv")
    @Operation(summary = "Export revenue report as CSV")
    public Mono<ResponseEntity<byte[]>> exportRevenueCsv(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        RevenueReportQuery query = new RevenueReportQuery(tenantId, ReportPeriod.of(from, to), currency);
        return reportingService.exportRevenueToCsv(query)
                .map(csv -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"revenue-" + from + "-" + to + ".csv\"")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(csv));
    }

    // ─── Commissions ─────────────────────────────────────────────────────────

    @GetMapping("/commissions")
    @Operation(summary = "Generate a commission summary for a given period")
    public Mono<CommissionSummaryResponse> getCommissionSummary(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        CommissionReportQuery query = new CommissionReportQuery(tenantId, ReportPeriod.of(from, to), currency);
        return reportingService.generateCommissionSummary(query).map(mapper::toResponse);
    }

    @GetMapping("/commissions/export/csv")
    @Operation(summary = "Export commissions as CSV")
    public Mono<ResponseEntity<byte[]>> exportCommissionsCsv(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        CommissionReportQuery query = new CommissionReportQuery(tenantId, ReportPeriod.of(from, to), currency);
        return reportingService.exportCommissionsToCsv(query)
                .map(csv -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"commissions-" + from + "-" + to + ".csv\"")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(csv));
    }

    // ─── Margins ─────────────────────────────────────────────────────────────

    @GetMapping("/margins")
    @Operation(summary = "Generate a margin report for a given period")
    public Mono<MarginReportResponse> getMarginReport(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        MarginReportQuery query = new MarginReportQuery(tenantId, ReportPeriod.of(from, to), currency);
        return reportingService.generateMarginReport(query).map(mapper::toResponse);
    }

    @GetMapping("/margins/export/csv")
    @Operation(summary = "Export margin report as CSV")
    public Mono<ResponseEntity<byte[]>> exportMarginCsv(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        MarginReportQuery query = new MarginReportQuery(tenantId, ReportPeriod.of(from, to), currency);
        return reportingService.exportMarginToCsv(query)
                .map(csv -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"margins-" + from + "-" + to + ".csv\"")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(csv));
    }

    // ─── KPI Snapshot ─────────────────────────────────────────────────────────

    @GetMapping("/kpi")
    @Operation(summary = "Get or refresh the current billing KPI snapshot")
    public Mono<BillingKPISnapshotResponse> getKPISnapshot(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "XAF") String currency) {
        KPISnapshotQuery query = new KPISnapshotQuery(tenantId, currency);
        return reportingService.getOrRefreshKPISnapshot(query).map(mapper::toResponse);
    }
    // ── : FreelancerOrg reports ───────────────────────────────────────────

    /**
     * GET /billing/reports/freelancer-org/{orgId}
     * Generates a financial report for a specific FreelancerOrg.
     */
    @GetMapping("/freelancer-org/{orgId}")
    public Mono<com.yowyob.tiibntick.core.billing.report.domain.model.FreelancerOrgReport>
            getFreelancerOrgReport(
                    @PathVariable String orgId,
                    @RequestParam java.util.UUID tenantId,
                    @RequestParam String from,
                    @RequestParam String to,
                    @RequestParam(defaultValue = "XAF") String currency) {
        var period = com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod.of(
                java.time.LocalDate.parse(from), java.time.LocalDate.parse(to));
        return reportingService.generateFreelancerOrgReport(
                new com.yowyob.tiibntick.core.billing.report.application.port.in.query.FreelancerOrgReportQuery(
                        tenantId, orgId, period, currency));
    }

    /**
     * GET /billing/reports/surcharge-analytics
     * Generates surcharge analytics for an actor's billing policy.
     */
    @GetMapping("/surcharge-analytics")
    public Mono<com.yowyob.tiibntick.core.billing.report.domain.model.SurchargeAnalyticsReport>
            getSurchargeAnalytics(
                    @RequestParam java.util.UUID tenantId,
                    @RequestParam String ownerOrgId,
                    @RequestParam String ownerOrgType,
                    @RequestParam String from,
                    @RequestParam String to,
                    @RequestParam(defaultValue = "XAF") String currency) {
        var period = com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod.of(
                java.time.LocalDate.parse(from), java.time.LocalDate.parse(to));
        return reportingService.generateSurchargeAnalytics(
                new com.yowyob.tiibntick.core.billing.report.application.port.in.query.SurchargeAnalyticsQuery(
                        tenantId, ownerOrgId, ownerOrgType, period, currency));
    }

    /**
     * GET /billing/reports/template-usage
     * Generates billing template usage analytics report.
     */
    @GetMapping("/template-usage")
    public Mono<com.yowyob.tiibntick.core.billing.report.domain.model.TemplateUsageReport>
            getTemplateUsageReport(
                    @RequestParam java.util.UUID tenantId,
                    @RequestParam(required = false) String templateCode,
                    @RequestParam String from,
                    @RequestParam String to,
                    @RequestParam(defaultValue = "XAF") String currency) {
        var period = com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod.of(
                java.time.LocalDate.parse(from), java.time.LocalDate.parse(to));
        return reportingService.generateTemplateUsageReport(
                new com.yowyob.tiibntick.core.billing.report.application.port.in.query.TemplateUsageReportQuery(
                        tenantId, templateCode, period, currency));
    }

}