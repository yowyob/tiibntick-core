package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClientApplication;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit n°7 · #4 remediation (2026-07-18) — {@code tnt-dispute-core} and
 * {@code tnt-sales-core} must accept EITHER an end-user JWT OR an internal platform
 * Client-Id/Api-Key call scoped to {@code DISPUTE:*}/{@code SALES:*}, never a raw
 * unauthenticated {@code X-Tenant-Id} header.
 *
 * <p>Mirrors {@link PlatformScopeAuthorizationManagerTest}'s style (pure decision-object
 * test, no full reactive security context needed).
 *
 * @author MANFOUO Braun
 */
class JwtOrPlatformScopeAuthorizationManagerTest {

    private final AuthorizationContext context = new AuthorizationContext(
            MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/disputes/anything").build()));

    private final JwtOrPlatformScopeAuthorizationManager disputeManager =
            new JwtOrPlatformScopeAuthorizationManager("DISPUTE", "*");

    @Test
    void grantsPlatformClientScopedToDispute() {
        Authentication auth = clientTokenWithScopes("DISPUTE:*");

        AuthorizationDecision decision = (AuthorizationDecision) disputeManager
                .authorize(Mono.just(auth), context).block();

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void deniesPlatformClientNotScopedToDispute() {
        Authentication auth = clientTokenWithScopes("SALES:*", "AUTH:*");

        AuthorizationDecision decision = (AuthorizationDecision) disputeManager
                .authorize(Mono.just(auth), context).block();

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void grantsOrdinaryAuthenticatedJwtLikePrincipal() {
        // Stand-in for a JwtAuthenticationToken: any authenticated, non-anonymous,
        // non-platform-client principal must be let through — tenant resolution happens
        // downstream via @CurrentUser TntUserIdentity, never here.
        Authentication jwtLike = new TestingAuthenticationToken("end-user", "n/a", "ROLE_CLIENT");
        jwtLike.setAuthenticated(true);

        AuthorizationDecision decision = (AuthorizationDecision) disputeManager
                .authorize(Mono.just(jwtLike), context).block();

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void deniesAnonymousAuthenticationToken() {
        // Regression guard: Spring WebFlux's default anonymous filter marks
        // AnonymousAuthenticationToken as isAuthenticated()==true. Without an explicit
        // instanceof check this would let a fully unauthenticated request (spoofed
        // X-Tenant-Id header, no credentials at all) pass as if it were JWT-backed.
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        AuthorizationDecision decision = (AuthorizationDecision) disputeManager
                .authorize(Mono.just(anonymous), context).block();

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void deniesWhenNoAuthenticationPresentAtAll() {
        AuthorizationDecision decision = (AuthorizationDecision) disputeManager
                .authorize(Mono.empty(), context).block();

        assertThat(decision.isGranted()).isFalse();
    }

    private static Authentication clientTokenWithScopes(String... scopes) {
        PlatformClientApplication principal = new PlatformClientApplication(
                UUID.randomUUID(), "tnt-agency-back-core", "AGENCY", Environment.PROD, Set.of(scopes));
        return new PlatformClientAuthenticationToken(principal);
    }
}
