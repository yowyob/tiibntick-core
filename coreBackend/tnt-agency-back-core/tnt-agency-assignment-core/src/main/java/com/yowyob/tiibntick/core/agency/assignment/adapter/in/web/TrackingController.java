package com.yowyob.tiibntick.core.agency.assignment.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.assignment.adapter.in.web.dto.TrackingResponse;
import com.yowyob.tiibntick.core.agency.assignment.application.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Tag(name = "Agency ERP Tracking", description = "Public parcel tracking by code")
@RestController
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/tracking/{trackingCode}")
    @Operation(summary = "Track parcel by tracking code (hub + optional mission)")
    public Mono<ApiResponse<TrackingResponse>> trackByCode(
            @PathVariable UUID tenantId,
            @PathVariable String trackingCode) {
        return trackingService.trackByCode(tenantId, trackingCode).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/hubs/{hubId}/tracking/{trackingCode}")
    @Operation(summary = "Track parcel by hub and tracking code")
    public Mono<ApiResponse<TrackingResponse>> trackByHubAndCode(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @PathVariable String trackingCode) {
        return trackingService.trackByHubAndCode(tenantId, hubId, trackingCode).map(ApiResponse::success);
    }
}
