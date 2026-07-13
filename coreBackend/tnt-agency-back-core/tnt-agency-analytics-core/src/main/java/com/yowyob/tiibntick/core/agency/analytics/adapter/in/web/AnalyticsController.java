package com.yowyob.tiibntick.core.agency.analytics.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.analytics.application.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Agency ERP analytics endpoints: dashboards and KPI reports.
 */
@Tag(name = "Agency ERP Analytics", description = "Dashboards and KPI reports")
@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/dashboard")
    @Operation(summary = "Agency dashboard KPIs")
    public Mono<ApiResponse<AnalyticsService.AgencyDashboard>> agencyDashboard(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return analyticsService.getAgencyDashboard(tenantId, agencyId).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/branches/{branchId}/dashboard")
    @Operation(summary = "Branch dashboard KPIs")
    public Mono<ApiResponse<AnalyticsService.BranchDashboard>> branchDashboard(
            @PathVariable UUID tenantId, @PathVariable UUID branchId) {
        return analyticsService.getBranchDashboard(tenantId, branchId).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/reports")
    @Operation(summary = "Hub occupancy and parcel status report")
    public Mono<ApiResponse<AnalyticsService.HubReport>> hubReports(
            @PathVariable UUID tenantId, @PathVariable UUID hubId) {
        return analyticsService.getHubReports(tenantId, hubId).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/reports")
    @Operation(summary = "Agency mission and commission KPI report")
    public Mono<ApiResponse<AnalyticsService.AgencyReport>> agencyReports(
            @PathVariable UUID tenantId, @PathVariable UUID agencyId) {
        return analyticsService.getAgencyReports(tenantId, agencyId).map(ApiResponse::success);
    }
}
