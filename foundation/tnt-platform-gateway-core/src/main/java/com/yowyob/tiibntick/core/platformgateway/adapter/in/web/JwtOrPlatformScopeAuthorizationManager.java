package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.yowyob.tiibntick.common.security.PermissionMatcher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Route-level authorization manager for endpoints that must accept EITHER an end-user
 * JWT OR an internal platform Client-Id/Api-Key call with a required scope — used by
 * {@code tnt-dispute-core} and {@code tnt-sales-core} (Audit n°7 · #4 remediation,
 * 2026-07-18: these two modules are called server-to-server by
 * {@code coreBackend/tnt-agency-back-core}'s outbound clients with no end-user JWT
 * available in Kafka-driven flows, so JWT-only tenant resolution — the fix already
 * applied to the other 9 affected modules — would break that internal integration).
 *
 * <p>Decision logic:
 * <ul>
 *   <li>{@link PlatformClientAuthenticationToken} → authorized only if the client's
 *       granted scopes satisfy one of {@link #acceptedScopes} (same wildcard semantics
 *       as {@link PlatformScopeAuthorizationManager}, via the shared
 *       {@code PermissionMatcher}).</li>
 *   <li>Any other authentication → authorized as long as it is genuinely authenticated
 *       AND NOT an {@link AnonymousAuthenticationToken}. This deliberately does not rely
 *       on {@code Authentication.isAuthenticated()} alone: Spring WebFlux's default
 *       anonymous filter marks {@code AnonymousAuthenticationToken} as
 *       "authenticated" too, which would otherwise let a fully unauthenticated request
 *       silently pass as if it were JWT-backed.</li>
 * </ul>
 *
 * <p>Tenant resolution itself happens downstream, in the controller, via
 * {@code @CurrentUser TntUserIdentity} (tnt-auth-core) — this manager only decides
 * whether the request may proceed at all, never reads/produces a tenant id.
 *
 * @author MANFOUO Braun
 */
public class JwtOrPlatformScopeAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final List<PlatformScopeAuthorizationManager.Scope> acceptedScopes;

    public JwtOrPlatformScopeAuthorizationManager(String resource, String action) {
        this(new PlatformScopeAuthorizationManager.Scope(resource, action));
    }

    public JwtOrPlatformScopeAuthorizationManager(PlatformScopeAuthorizationManager.Scope... acceptedScopes) {
        this.acceptedScopes = List.of(acceptedScopes);
    }

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .<AuthorizationResult>map(auth -> {
                    if (!auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                        return new AuthorizationDecision(false);
                    }
                    if (auth instanceof PlatformClientAuthenticationToken platformToken) {
                        boolean scopeSatisfied = acceptedScopes.stream().anyMatch(scope ->
                                PermissionMatcher.matchesAny(platformToken.getScopes(), scope.resource(), scope.action()));
                        return new AuthorizationDecision(scopeSatisfied);
                    }
                    // Any other genuinely-authenticated principal (end-user JWT, or the
                    // dev allow-anonymous-context synthetic token) is accepted here — the
                    // controller resolves the tenant from it, never from a raw header.
                    return new AuthorizationDecision(true);
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
