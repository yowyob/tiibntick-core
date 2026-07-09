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
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Validates {@code X-Client-Id}/{@code X-Api-Key} for every request on the platform
 * gateway's own {@link org.springframework.security.web.server.SecurityWebFilterChain}
 * (see {@code TntPlatformGatewaySecurityConfig}, {@code @Order(10)}) — the mechanism
 * that lets TiiBnTick Core tell which platform backend (Agency, Go, ...) is calling,
 * distinct from any end-user JWT the request may also carry.
 *
 * <p>On success, builds a {@link PlatformClientAuthenticationToken} (scopes as
 * authorities) and pushes it into the reactive {@code SecurityContext} via
 * {@code contextWrite} — required because this chain uses
 * {@code NoOpServerSecurityContextRepository} (stateless), so nothing else populates
 * it. This is what makes {@code PlatformScopeAuthorizationManager} (route-level) and
 * {@code PlatformScopeAspect}/{@code @RequirePlatformScope} (per-endpoint) able to see
 * the authenticated client's scopes downstream.
 *
 * <p>Never validates a Kernel credential — Kernel's {@code X-Api-Key}/{@code X-Client-Id}
 * stay server-side only (see {@code KernelBridgeConfig}); this filter checks the
 * platform's OWN credential pair via {@link PlatformClientAuthenticationService}.
 *
 * <p>Every attempt (success or failure) is recorded via {@link PlatformClientAuditRecorder}
 * on a fire-and-forget basis — never adds latency to this filter's response path.
 *
 * @author MANFOUO Braun
 */
public class PlatformApiKeyWebFilter implements WebFilter {

    private final PlatformClientAuthenticationService authenticationService;
    private final PlatformClientAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public PlatformApiKeyWebFilter(
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
        String endpoint = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        String ipAddress = resolveIp(exchange);
        String userAgent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);

        return authenticationService.authenticate(clientId, apiKey)
                .flatMap(principal -> proceed(exchange, chain, principal, clientId, endpoint, method, ipAddress, userAgent))
                .onErrorResume(TntPlatformGatewayException.class,
                        ex -> reject(exchange, clientId, endpoint, method, ipAddress, userAgent));
    }

    private Mono<Void> proceed(ServerWebExchange exchange, WebFilterChain chain, PlatformClientApplication principal,
            String clientId, String endpoint, String method, String ipAddress, String userAgent) {
        auditRecorder.record(principal.id(), clientId, endpoint, method, AuditOutcome.SUCCESS, ipAddress, userAgent);
        PlatformClientAuthenticationToken token = new PlatformClientAuthenticationToken(principal);
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token));
    }

    private Mono<Void> reject(ServerWebExchange exchange, String clientIdAttempted, String endpoint, String method,
            String ipAddress, String userAgent) {
        AuditOutcome outcome = (clientIdAttempted == null || clientIdAttempted.isBlank())
                ? AuditOutcome.UNKNOWN_CLIENT : AuditOutcome.INVALID_KEY;
        auditRecorder.record(null, clientIdAttempted, endpoint, method, outcome, ipAddress, userAgent);

        ApiResponse<Void> body = ApiResponse.error(
                ErrorDetail.of("PLATFORM_UNAUTHORIZED", "Missing or invalid X-Client-Id / X-Api-Key"), null);
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

    private static String resolveIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : null;
    }
}
