package com.yowyob.tiibntick.bootstrap.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Locks in the two security perimeters defined by {@code TntSecurityConfig} /
 * {@code TntAuthGatewaySecurityConfig} against regressions like the one fixed in
 * commit 2cd0b23 (a {@code @Bean}-scoped {@link org.springframework.web.server.WebFilter}
 * silently applying to every route instead of just its intended {@code securityMatcher}).
 *
 * <p>Boots the real, fully-assembled application context (not a security-only slice) so
 * that any {@code @Order} chain mis-registration, global filter leakage, or accidental
 * path removal is caught here — before a deploy — instead of via a manual {@code curl}
 * against production.
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
@ActiveProfiles({"test", "r2dbc"})
class PublicPathsSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Every path declared in {@code TntSecurityConfig.PUBLIC_PATHS} must be reachable
     * without any credentials. This does not assert 200 (some, like {@code /actuator/health}
     * readiness groups, legitimately return 503 without a live DB/Kafka/Redis in this test
     * slice) — only that authentication/authorization never causes the rejection.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/swagger-ui/index.html",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/.well-known/openid-configuration",
            "/.well-known/oauth-authorization-server",
    })
    void publicPathsAreReachableWithoutAuthentication(String path) {
        webTestClient.get().uri(path)
                .exchange()
                .expectStatus().value(status -> {
                    if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                        throw new AssertionError(
                                "Public path " + path + " must never require authentication, got HTTP " + status);
                    }
                });
    }

    /**
     * The platform gateway paths ({@code /api/v1/auth/**}, {@code /api/v1/sso/**},
     * {@code /api/v1/onboarding/**}) are exempt from the end-user JWT requirement, but
     * they are NOT public: {@link com.yowyob.tiibntick.core.auth.adapter.in.web.PlatformApiKeyWebFilter}
     * must still reject calls missing X-Client-Id/X-Api-Key. This is the counterpart
     * assertion to the test above — proving the fix didn't overcorrect into making
     * these paths public too.
     */
    @Test
    void platformGatewayPathsStillRequireApiKeyCredentials() {
        webTestClient.post().uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * Proves {@code TntRoleAssignmentAdminController} (tnt-roles-core, component-scanned —
     * not an explicit {@code @Bean}) is actually registered in the assembled application
     * context and published in the generated OpenAPI doc under its {@code @Tag}, the same
     * way {@code PlatformClientAdminController} is. A missing entry here would mean the
     * blanket {@code @ComponentScan} in {@code TiiBnTickApplication} isn't picking up the
     * controller, or springdoc isn't seeing it.
     */
    @Test
    void roleAssignmentAdminEndpointIsPublishedInOpenApiDocs() {
        // The full OpenAPI doc (every module's controllers) exceeds WebTestClient's default
        // 256KB in-memory body limit — raise it just for this call.
        WebTestClient largeBodyClient = webTestClient.mutate()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
        largeBodyClient.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    if (!body.contains("\"/api/v1/admin/roles/assignments\"")) {
                        throw new AssertionError(
                                "Expected /api/v1/admin/roles/assignments in /v3/api-docs but it was missing");
                    }
                    if (!body.contains("Role Assignment Admin")) {
                        throw new AssertionError(
                                "Expected the 'Role Assignment Admin' tag in /v3/api-docs but it was missing");
                    }
                });
    }
}
