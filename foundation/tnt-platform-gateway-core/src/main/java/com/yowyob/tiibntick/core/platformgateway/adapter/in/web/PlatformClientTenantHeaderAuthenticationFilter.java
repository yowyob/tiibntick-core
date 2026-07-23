package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuditRecorder;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuthenticationService;
import com.yowyob.tiibntick.core.platformgateway.domain.exception.TntPlatformGatewayException;
import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClientApplication;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Conditional {@code X-Client-Id}/{@code X-Api-Key} authentication for the dual-auth
 * chain shared by {@code tnt-dispute-core} and {@code tnt-sales-core}
 * ({@code TntPlatformGatewaySecurityConfig}, {@code @Order(11)}).
 *
 * <p>Unlike {@link PlatformApiKeyWebFilter} (which unconditionally requires the
 * headers on its own {@code @Order(10)} chain), this filter is a no-op pass-through
 * when {@code X-Client-Id}/{@code X-Api-Key} are absent — deferring entirely to the
 * chain's {@code oauth2ResourceServer} JWT authentication for ordinary end-user calls.
 * It only engages when both headers are present, which is exactly the signal that the
 * caller intends the internal server-to-server mechanism (Audit n°7 · #4 remediation,
 * 2026-07-18 — see {@code JwtOrPlatformScopeAuthorizationManager}).
 *
 * <p>On success, also validates {@code X-Tenant-Id} as a well-formed UUID and attaches
 * it as a synthetic {@code TENANT_<uuid>} authority on the resulting
 * {@link PlatformClientAuthenticationToken} — this is what lets
 * {@code tnt-auth-core}'s {@code TntSecurityContextService}/{@code @CurrentUser
 * TntUserIdentity} resolve the tenant transparently for platform-client calls too,
 * with zero changes to tnt-auth-core. The header is trusted ONLY at this point:
 * reaching here already required a valid, scoped, audited Client-Id/Api-Key pair —
 * this is what closes Audit n°7 · #4 for these two modules without abandoning the
 * "tenant carried in the request" shape the internal integration relies on (agency-back-core
 * is itself a multi-tenant proxy; a platform client identifies WHICH BACKEND is
 * calling, never a single tenant — see {@code PlatformClient}, no tenant field).
 *
 * <p>Also attaches literal {@code sales:read}/{@code sales:write} synthetic authorities
 * for calls under {@code /api/sales/orders/**} — {@code SalesOrderController}'s existing
 * {@code @PreAuthorize("hasAuthority('sales:write')")}/{@code ('sales:read')} guards do an
 * EXACT string match (Spring's native {@code hasAuthority()}, not the wildcard-aware
 * {@code PermissionMatcher}), so they would otherwise reject even a fully-authenticated,
 * correctly-scoped ({@code SALES:*}) platform-client call. This keeps the two annotations
 * untouched and keeps wildcard-scope matching confined to
 * {@link JwtOrPlatformScopeAuthorizationManager} (route level), never duplicated into
 * {@code hasAuthority()} SpEL strings. {@code tnt-dispute-core} has no equivalent
 * {@code @PreAuthorize} guards today, so no extra authority is attached for
 * {@code /api/v1/disputes/**}.
 *
 * @author MANFOUO Braun
 */
public class PlatformClientTenantHeaderAuthenticationFilter implements WebFilter {

    private static final String SALES_PATH_PREFIX = "/api/sales/orders";

    private final PlatformClientAuthenticationService authenticationService;
    private final PlatformClientAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public PlatformClientTenantHeaderAuthenticationFilter(
            PlatformClientAuthenticationService authenticationService,
            PlatformClientAuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-Id");
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");

        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            // No platform-client credentials presented at all — this is an ordinary
            // end-user call, let the chain's oauth2ResourceServer JWT filter handle it.
            return chain.filter(exchange);
        }

        String endpoint = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        String ipAddress = resolveIp(exchange);
        String userAgent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        String tenantHeader = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        UUID tenantId = parseUuidOrNull(tenantHeader);

        if (tenantId == null) {
            auditRecorder.record(null, clientId, endpoint, method, AuditOutcome.INVALID_KEY, ipAddress, userAgent);
            return reject(exchange, "Missing or malformed X-Tenant-Id header for a platform-client call");
        }

        return authenticationService.authenticate(clientId, apiKey)
                .flatMap(principal -> proceed(exchange, chain, principal, tenantId, clientId, endpoint, method, ipAddress, userAgent))
                .onErrorResume(TntPlatformGatewayException.class,
                        ex -> {
                            auditRecorder.record(null, clientId, endpoint, method, AuditOutcome.INVALID_KEY, ipAddress, userAgent);
                            return reject(exchange, "Missing or invalid X-Client-Id / X-Api-Key");
                        });
    }

    private Mono<Void> proceed(ServerWebExchange exchange, WebFilterChain chain, PlatformClientApplication principal,
            UUID tenantId, String clientId, String endpoint, String method, String ipAddress, String userAgent) {
        auditRecorder.record(principal.id(), clientId, endpoint, method, AuditOutcome.SUCCESS, ipAddress, userAgent);
        List<SimpleGrantedAuthority> extraAuthorities = endpoint.startsWith(SALES_PATH_PREFIX)
                ? List.of(new SimpleGrantedAuthority("TENANT_" + tenantId),
                        new SimpleGrantedAuthority("sales:read"),
                        new SimpleGrantedAuthority("sales:write"))
                : List.of(new SimpleGrantedAuthority("TENANT_" + tenantId));
        PlatformClientAuthenticationToken token = new PlatformClientAuthenticationToken(principal, extraAuthorities);
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token));
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ApiResponse<Void> body = ApiResponse.error(ErrorDetail.of("PLATFORM_UNAUTHORIZED", message), null);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"status\":\"ERROR\"}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(bytes)));
    }

    private static UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String resolveIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : null;
    }
}
