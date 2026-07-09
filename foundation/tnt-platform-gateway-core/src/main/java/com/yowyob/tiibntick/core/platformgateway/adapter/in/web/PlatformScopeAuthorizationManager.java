package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.yowyob.tiibntick.common.security.PermissionMatcher;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

/**
 * Coarse, route-level scope check for one gateway block (e.g. {@code AUTH:*} for
 * {@code /api/v1/auth/**}) — wired via {@code .pathMatchers(...).access(...)} in
 * {@code TntPlatformGatewaySecurityConfig}.
 *
 * <p>Deliberately NOT Spring's built-in {@code hasAuthority()}: that primitive does an
 * exact string match and would silently fail to honor a {@code resource:*} or {@code *}
 * wildcard scope grant. This manager instead evaluates the current
 * {@link PlatformClientAuthenticationToken}'s scopes through the same
 * {@code PermissionMatcher} used everywhere else in the codebase — one wildcard
 * semantics, no drift (see
 * {@code docs/auth/platform-client-management-design.md} §2.4/§7).
 *
 * @author MANFOUO Braun
 */
public class PlatformScopeAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final String resource;
    private final String action;

    public PlatformScopeAuthorizationManager(String resource, String action) {
        this.resource = resource;
        this.action = action;
    }

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(PlatformClientAuthenticationToken.class::isInstance)
                .cast(PlatformClientAuthenticationToken.class)
                .<AuthorizationResult>map(token -> new AuthorizationDecision(PermissionMatcher.matchesAny(token.getScopes(), resource, action)))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
