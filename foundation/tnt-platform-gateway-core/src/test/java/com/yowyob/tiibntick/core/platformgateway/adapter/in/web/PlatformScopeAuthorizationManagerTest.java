package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClientApplication;
import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the OR-of-multiple-scopes behaviour added to {@link PlatformScopeAuthorizationManager}
 * for Audit n°7 · #15: {@code /api/v1/platform/**} must require either {@code MARKET:*} or
 * {@code LINK:*} — before this fix it fell through to
 * {@code TntPlatformGatewaySecurityConfig}'s {@code .anyExchange().permitAll()}, so ANY
 * successfully authenticated platform client (even one scoped to AUTH/SSO/ONBOARDING only)
 * could reach it.
 *
 * <p>Exercises the real authorization decision object used by the security chain
 * ({@code .access(new PlatformScopeAuthorizationManager(...))}) directly, without booting a
 * full reactive web security context — this module has no existing web-slice test
 * infrastructure to reuse, and the decision under test is a pure function of the
 * authenticated token's granted scopes.
 *
 * @author MANFOUO Braun
 */
class PlatformScopeAuthorizationManagerTest {

    private final AuthorizationContext context = new AuthorizationContext(
            MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/platform/anything").build()));

    private final PlatformScopeAuthorizationManager platformBlockManager = new PlatformScopeAuthorizationManager(
            new PlatformScopeAuthorizationManager.Scope("MARKET", "*"),
            new PlatformScopeAuthorizationManager.Scope("LINK", "*"));

    @Test
    void deniesClientScopedOnlyToAuthSsoOnboarding() {
        Authentication authRequest = clientTokenWithScopes("AUTH:*", "SSO:*", "ONBOARDING:*");

        AuthorizationDecision decision = (AuthorizationDecision) platformBlockManager
                .authorize(Mono.just(authRequest), context).block();

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void grantsClientScopedToMarket() {
        Authentication authRequest = clientTokenWithScopes("MARKET:*");

        AuthorizationDecision decision = (AuthorizationDecision) platformBlockManager
                .authorize(Mono.just(authRequest), context).block();

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void grantsClientScopedToLink() {
        Authentication authRequest = clientTokenWithScopes("LINK:*");

        AuthorizationDecision decision = (AuthorizationDecision) platformBlockManager
                .authorize(Mono.just(authRequest), context).block();

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void deniesNonPlatformClientAuthentication() {
        // A non-PlatformClientAuthenticationToken Authentication (e.g. stray JWT auth
        // leaking into this chain) must never be granted access to a platform-scoped block.
        Authentication notAPlatformClient = new TestingAuthenticationToken("someone", "n/a", "ROLE_TNT_ADMIN");
        notAPlatformClient.setAuthenticated(true);

        AuthorizationDecision decision = (AuthorizationDecision) platformBlockManager
                .authorize(Mono.just(notAPlatformClient), context).block();

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void singleScopeConstructorStillWorksForExistingAuthSsoOnboardingBlocks() {
        PlatformScopeAuthorizationManager authOnly = new PlatformScopeAuthorizationManager("AUTH", "*");

        AuthorizationDecision granted = (AuthorizationDecision) authOnly
                .authorize(Mono.just(clientTokenWithScopes("AUTH:*")), context).block();
        AuthorizationDecision denied = (AuthorizationDecision) authOnly
                .authorize(Mono.just(clientTokenWithScopes("SSO:*")), context).block();

        assertThat(granted.isGranted()).isTrue();
        assertThat(denied.isGranted()).isFalse();
    }

    private static Authentication clientTokenWithScopes(String... scopes) {
        PlatformClientApplication principal = new PlatformClientApplication(
                UUID.randomUUID(), "test-client", "MARKET", Environment.PROD, Set.of(scopes));
        return new PlatformClientAuthenticationToken(principal);
    }
}
