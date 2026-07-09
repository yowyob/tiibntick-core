package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.PagedResult;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.ApiKeyResponse;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.ClientAuditLogResponse;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.ClientPermissionResponse;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.CreatePlatformClientRequest;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.IssueApiKeyRequest;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.IssuedApiKeyResponse;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.PlatformClientResponse;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.ReplaceScopesRequest;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto.UpdatePlatformClientRequest;
import com.yowyob.tiibntick.core.platformgateway.application.port.in.PlatformClientAdminUseCase;
import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD for platform clients, their API keys, granted scopes, and audit trail —
 * usable ONLY by {@code TNT_ADMIN} (see
 * {@code docs/auth/platform-client-management-design.md} §4). This is a normal
 * JWT-authenticated endpoint (the standard catch-all security chain,
 * {@code TntSecurityConfig} @Order(20) in tnt-bootstrap) — NOT part of the platform
 * gateway's own {@code X-Client-Id}/{@code X-Api-Key} chain, since the caller here is a
 * human TiiBnTick administrator, not a platform backend.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/admin/platform-clients")
@RequirePermission(resource = "platform", action = "clients")
@Tag(name = "Platform Client Admin", description = "TNT_ADMIN-only management of platform clients, API keys, scopes, and audit trail")
public class PlatformClientAdminController {

    private final PlatformClientAdminUseCase useCase;

    public PlatformClientAdminController(PlatformClientAdminUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    @Operation(summary = "Register a new platform client")
    public Mono<ResponseEntity<ApiResponse<PlatformClientResponse>>> create(
            @RequestBody CreatePlatformClientRequest request,
            @CurrentUser TntSecurityContext admin) {
        return useCase.createClient(request.name(), request.platformCode(), request.environment(),
                        request.description(), request.contactEmail(), actorId(admin))
                .map(PlatformClientResponse::from)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response)));
    }

    @GetMapping
    @Operation(summary = "List/search platform clients, paginated")
    public Mono<ApiResponse<PagedResult<PlatformClientResponse>>> list(
            @RequestParam(required = false) String platformCode,
            @RequestParam(required = false) Environment environment,
            @RequestParam(required = false) ClientStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return useCase.listClients(platformCode, environment, status, page, size)
                .map(PlatformClientResponse::from)
                .collectList()
                .zipWith(useCase.countClients(platformCode, environment, status))
                .map(tuple -> ApiResponse.success(PagedResult.of(tuple.getT1(), page, size, tuple.getT2())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Platform client detail")
    public Mono<ApiResponse<PlatformClientResponse>> get(@PathVariable UUID id) {
        return useCase.getClient(id).map(PlatformClientResponse::from).map(ApiResponse::success);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update name/description/contact/status")
    public Mono<ApiResponse<PlatformClientResponse>> update(
            @PathVariable UUID id,
            @RequestBody UpdatePlatformClientRequest request,
            @CurrentUser TntSecurityContext admin) {
        return useCase.updateClient(id, request.name(), request.description(), request.contactEmail(),
                        request.status(), actorId(admin))
                .map(PlatformClientResponse::from)
                .map(ApiResponse::success);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-decommission — revokes all active keys, never a hard delete")
    public Mono<ResponseEntity<Void>> decommission(@PathVariable UUID id, @CurrentUser TntSecurityContext admin) {
        return useCase.decommissionClient(id, actorId(admin))
                .thenReturn(ResponseEntity.noContent().build());
    }

    // ── API keys ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/api-keys")
    @Operation(summary = "Issue a new API key — plaintext secret shown exactly once in the response")
    public Mono<ResponseEntity<ApiResponse<IssuedApiKeyResponse>>> issueApiKey(
            @PathVariable UUID id,
            @RequestBody(required = false) IssueApiKeyRequest request,
            @CurrentUser TntSecurityContext admin) {
        Instant expiresAt = request != null ? request.expiresAt() : null;
        return useCase.issueApiKey(id, expiresAt, actorId(admin))
                .map(IssuedApiKeyResponse::from)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response)));
    }

    @GetMapping("/{id}/api-keys")
    @Operation(summary = "List keys (prefix, status, expiry) — never the secret/hash")
    public Mono<ApiResponse<java.util.List<ApiKeyResponse>>> listApiKeys(@PathVariable UUID id) {
        return useCase.listApiKeys(id).map(ApiKeyResponse::from).collectList().map(ApiResponse::success);
    }

    // ── Scopes ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/permissions")
    @Operation(summary = "Replace the client's entire granted scope set")
    public Mono<ResponseEntity<Void>> replaceScopes(
            @PathVariable UUID id,
            @RequestBody ReplaceScopesRequest request,
            @CurrentUser TntSecurityContext admin) {
        Set<String> scopes = request.scopes() != null ? request.scopes() : Set.of();
        return useCase.replaceScopes(id, scopes, actorId(admin))
                .thenReturn(ResponseEntity.ok().<Void>build());
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "Current granted scope set")
    public Mono<ApiResponse<java.util.List<ClientPermissionResponse>>> getScopes(@PathVariable UUID id) {
        return useCase.getScopes(id).map(ClientPermissionResponse::from).collectList().map(ApiResponse::success);
    }

    // ── Audit ─────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/audit-logs")
    @Operation(summary = "Paginated audit trail for this client")
    public Mono<ApiResponse<PagedResult<ClientAuditLogResponse>>> auditLogs(
            @PathVariable UUID id,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return useCase.listAuditLogs(id, outcome, from, to, page, size)
                .map(ClientAuditLogResponse::from)
                .collectList()
                .zipWith(useCase.countAuditLogs(id, outcome, from, to))
                .map(tuple -> ApiResponse.success(PagedResult.of(tuple.getT1(), page, size, tuple.getT2())));
    }

    private static String actorId(TntSecurityContext admin) {
        return admin != null && admin.userId() != null ? admin.userId().toString() : null;
    }
}
