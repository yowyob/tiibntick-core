package com.yowyob.tiibntick.core.billing.report.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.report.adapter.in.web.dto.response.*;
import com.yowyob.tiibntick.core.billing.report.adapter.in.web.mapper.ReportWebMapper;
import com.yowyob.tiibntick.core.billing.report.application.port.in.query.*;
import com.yowyob.tiibntick.core.billing.report.application.service.ReportingService;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final ReportAccessPolicy access;

    public ReportingController(ReportingService reportingService, ReportWebMapper mapper,
                               ReportAccessPolicy access) {
        this.reportingService = reportingService;
        this.mapper = mapper;
        this.access = access;
    }

    // ─── Revenue ─────────────────────────────────────────────────────────────

    @GetMapping("/revenue")
    @Operation(summary = "Generate a revenue report for a given period")
    public Mono<RevenueReportResponse> getRevenueReport(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        RevenueReportQuery query = new RevenueReportQuery(currentUser.tenantId(), ReportPeriod.of(from, to), currency);
        return reportingService.generateRevenueReport(query).map(mapper::toResponse);
    }

    @GetMapping("/revenue/export/csv")
    @Operation(summary = "Export revenue report as CSV")
    public Mono<ResponseEntity<byte[]>> exportRevenueCsv(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        RevenueReportQuery query = new RevenueReportQuery(currentUser.tenantId(), ReportPeriod.of(from, to), currency);
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
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        CommissionReportQuery query = new CommissionReportQuery(currentUser.tenantId(), ReportPeriod.of(from, to), currency);
        return reportingService.generateCommissionSummary(query).map(mapper::toResponse);
    }

    @GetMapping("/commissions/export/csv")
    @Operation(summary = "Export commissions as CSV")
    public Mono<ResponseEntity<byte[]>> exportCommissionsCsv(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        CommissionReportQuery query = new CommissionReportQuery(currentUser.tenantId(), ReportPeriod.of(from, to), currency);
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
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        MarginReportQuery query = new MarginReportQuery(currentUser.tenantId(), ReportPeriod.of(from, to), currency);
        return reportingService.generateMarginReport(query).map(mapper::toResponse);
    }

    @GetMapping("/margins/export/csv")
    @Operation(summary = "Export margin report as CSV")
    public Mono<ResponseEntity<byte[]>> exportMarginCsv(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        MarginReportQuery query = new MarginReportQuery(currentUser.tenantId(), ReportPeriod.of(from, to), currency);
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
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(defaultValue = "XAF") String currency) {
        KPISnapshotQuery query = new KPISnapshotQuery(currentUser.tenantId(), currency);
        return reportingService.getOrRefreshKPISnapshot(query).map(mapper::toResponse);
    }
    // ── Per-issuer reports ────────────────────────────────────────────────

    /**
     * GET /api/v1/billing/reports/freelancer-org/{orgId}
     *
     * <p>Financial report of one driver / FreelancerOrg. The tenant is always the caller's (it used to be a
     * request parameter, which let any holder of {@code report:read} read another tenant). Management reads any
     * issuer of their tenant; a driver reads only their own id.
     */
    @GetMapping("/freelancer-org/{orgId}")
    @Operation(summary = "Financial report of a driver or FreelancerOrg (own id for drivers)")
    public Mono<com.yowyob.tiibntick.core.billing.report.domain.model.FreelancerOrgReport> getFreelancerOrgReport(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        FreelancerOrgReportQuery query = new FreelancerOrgReportQuery(
                currentUser.tenantId(), orgId, ReportPeriod.of(from, to), currency);
        return access.isStaff().flatMap(staff -> staff
                ? reportingService.generateFreelancerOrgReport(query)
                : ownOnly(currentUser, orgId, () -> reportingService.generateOwnFreelancerReport(query)));
    }

    /** GET /api/v1/billing/reports/surcharge-analytics — surcharge revenue of one issuer. */
    @GetMapping("/surcharge-analytics")
    @Operation(summary = "Surcharge analytics of an issuer (own id for drivers)")
    public Mono<com.yowyob.tiibntick.core.billing.report.domain.model.SurchargeAnalyticsReport> getSurchargeAnalytics(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam String ownerOrgId,
            @RequestParam String ownerOrgType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        SurchargeAnalyticsQuery query = new SurchargeAnalyticsQuery(
                currentUser.tenantId(), ownerOrgId, ownerOrgType, ReportPeriod.of(from, to), currency);
        return access.isStaff().flatMap(staff -> staff
                ? reportingService.generateSurchargeAnalytics(query)
                : ownOnly(currentUser, ownerOrgId, () -> reportingService.generateOwnSurchargeAnalytics(query)));
    }

    /** GET /api/v1/billing/reports/template-usage — management only (report:read), caller's tenant. */
    @GetMapping("/template-usage")
    @Operation(summary = "Billing template usage over a period")
    public Mono<com.yowyob.tiibntick.core.billing.report.domain.model.TemplateUsageReport> getTemplateUsageReport(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(required = false) String templateCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "XAF") String currency) {
        return reportingService.generateTemplateUsageReport(new TemplateUsageReportQuery(
                currentUser.tenantId(), templateCode, ReportPeriod.of(from, to), currency));
    }

    private static <T> Mono<T> ownOnly(TntUserIdentity user, String issuerId,
                                       java.util.function.Supplier<Mono<T>> own) {
        if (!ReportAccessPolicy.isOwn(user, issuerId)) {
            return Mono.error(com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException
                    .forbidden("report", "read"));
        }
        return own.get();
    }

}
