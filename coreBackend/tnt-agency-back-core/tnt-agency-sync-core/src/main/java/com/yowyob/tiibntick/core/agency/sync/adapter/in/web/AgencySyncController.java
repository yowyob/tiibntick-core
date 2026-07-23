package com.yowyob.tiibntick.core.agency.sync.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.sync.adapter.in.web.dto.SyncPullResult;
import com.yowyob.tiibntick.core.agency.sync.application.service.AgencySyncService;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/** Port of tnt-agency {@code SyncController} — agency-scoped offline sync. */
@Tag(name = "Agency ERP Sync", description = "Offline delta sync for agency mobile clients")
@RestController
@RequiredArgsConstructor
public class AgencySyncController {

    private final AgencySyncService syncService;

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/sync/pull")
    @Operation(summary = "Pull sync delta from Core engine")
    public Mono<ApiResponse<SyncPullResult>> pull(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestParam(required = false) String syncToken,
            @RequestParam(required = false) Set<String> filter) {
        return syncService.pull(new AgencySyncService.PullInput(
                tenantId, agencyId, userId,
                deviceId != null ? deviceId : "web",
                syncToken, filter
        )).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/sync/bootstrap")
    @Operation(summary = "Bootstrap full delta for first-time device sync")
    public Mono<ApiResponse<SyncPullResult>> bootstrap(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestParam(required = false) Set<String> filter) {
        return syncService.bootstrap(new AgencySyncService.BootstrapInput(
                tenantId, agencyId, userId,
                deviceId != null ? deviceId : "web",
                filter
        )).map(ApiResponse::success);
    }

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/sync/push")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Push offline operations to Core engine (returns conflicts)")
    public Mono<ApiResponse<SyncPushResponse>> push(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestParam(required = false) String syncToken,
            @RequestBody PushRequest body) {
        return syncService.push(new AgencySyncService.PushInput(
                tenantId, agencyId, userId,
                deviceId != null ? deviceId : "web",
                syncToken,
                body.operations() != null ? body.operations() : List.of()
        )).map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/sync/schema/duckdb")
    @Operation(summary = "DuckDB-Wasm DDL schema for agency mobile client")
    public Mono<ApiResponse<DuckDbSchemaResponse>> duckDbSchema(@PathVariable UUID tenantId) {
        return syncService.duckDbSchema(tenantId)
                .map(ddl -> ApiResponse.success(new DuckDbSchemaResponse(ddl, "1.0")));
    }

    record PushRequest(List<Map<String, Object>> operations) {}

    record DuckDbSchemaResponse(String ddl, String version) {}
}
