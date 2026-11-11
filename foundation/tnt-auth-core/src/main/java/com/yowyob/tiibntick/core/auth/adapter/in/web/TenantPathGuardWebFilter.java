package com.yowyob.tiibntick.core.auth.adapter.in.web;

import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refuses a request whose URL names another tenant than the caller's own.
 *
 * <p>Several modules (delivery, geo, agency back-cores) expose
 * {@code /api/v1/tenants/{tenantId}/...} and use that path variable directly as the tenant of the
 * query or command. Nothing compared it with the tenant of the authenticated token, so any
 * authenticated user could read or write another tenant's data by editing the URL.
 *
 * <p>The guard only rejects a <em>proven</em> mismatch: the caller is authenticated with a tenant
 * and the URL carries a different, well-formed tenant id. Callers holding the {@code *} permission
 * (platform administrators) may cross tenants. Requests that are not authenticated through a
 * human JWT (platform clients, anonymous routes) are left to their own security chain.
 *
 * <p>It runs after Spring Security's chain (order {@code -100}) so the authentication is available.
 */
@Order(TenantPathGuardWebFilter.ORDER)
public class TenantPathGuardWebFilter implements WebFilter {

    /** Right after the Spring Security web filter chain (order -100). */
    public static final int ORDER = -50;

    private static final Logger log = LoggerFactory.getLogger(TenantPathGuardWebFilter.class);
    private static final Pattern TENANT_PATH = Pattern.compile("^/api/v1/tenants/([^/]+)(?:/.*)?$");

    private final ResolveCurrentUserUseCase currentUser;

    public TenantPathGuardWebFilter(ResolveCurrentUserUseCase currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Matcher m = TENANT_PATH.matcher(exchange.getRequest().getPath().pathWithinApplication().value());
        if (!m.matches()) {
            return chain.filter(exchange);
        }
        UUID pathTenant;
        try {
            pathTenant = UUID.fromString(m.group(1));
        } catch (IllegalArgumentException notAUuid) {
            return chain.filter(exchange); // the controller answers 400 for a malformed id
        }
        return currentUser.resolveCurrentContextOrAnonymous()
                .defaultIfEmpty(TntSecurityContext.anonymous())
                .flatMap(ctx -> {
                    if (!ctx.isFullyAuthenticated() || pathTenant.equals(ctx.tenantId())
                            || ctx.permissions().contains("*")) {
                        return chain.filter(exchange);
                    }
                    log.warn("Tenant path guard: user {} of tenant {} asked for tenant {} on {}",
                            ctx.userId(), ctx.tenantId(), pathTenant, exchange.getRequest().getPath());
                    return forbidden(exchange);
                });
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"The tenant in the URL is not your tenant.\"}"
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
