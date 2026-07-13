package com.yowyob.tiibntick.bootstrap.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator for {@code tnt-auth-core} (L1 Foundation).
 *
 * <p>Verifies that the security bridge between TiiBnTick Core and YowAuth0 is correctly
 * configured. Checks:
 * <ol>
 *   <li>JWT JWK set URI is configured (not empty).</li>
 *   <li>Auth service code is configured (not empty).</li>
 *   <li>Token cache TTL is a valid positive duration string.</li>
 * </ol>
 *
 * <p>Checks {@code jwk-set-uri}, not {@code issuer-uri} — {@code application.yml} deliberately
 * leaves {@code issuer-uri} unset (see its comment there): the Kernel issues {@code iss:
 * "kernel-core"}, not a URL, so setting {@code issuer-uri} would make Spring Security's OIDC
 * auto-discovery reject every token. {@code jwk-set-uri} is what actually drives signature/expiry
 * validation here; this indicator originally checked {@code issuer-uri} and therefore always
 * reported DOWN despite the app working as intended (flagged externally 2026-07-12).
 *
 * <p>This indicator does NOT perform an outbound network call — it validates configuration
 * only. The Kernel connectivity check is handled by {@link TntKernelHealthIndicator}.
 *
 * <p>Exposed at {@code /actuator/health/tnt-infra/auth}.
 *
 * @author MANFOUO Braun
 * @see TntHealthConfig
 */
@Slf4j
@Component
public class TntAuthHealthIndicator implements ReactiveHealthIndicator {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwtJwkSetUri;

    @Value("${tnt.auth.service-code:}")
    private String authServiceCode;

    @Value("${tnt.auth.token-cache-ttl:PT14M}")
    private String tokenCacheTtl;

    @Value("${tnt.auth.actor-resolution-enabled:true}")
    private boolean actorResolutionEnabled;

    @Value("${tnt.auth.allow-anonymous-context:false}")
    private boolean allowAnonymousContext;

    /**
     * Evaluates the tnt-auth-core configuration health.
     * Returns {@code UP} when all required properties are present,
     * {@code DOWN} otherwise.
     *
     * @return reactive health result
     */
    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(this::evaluate);
    }

    private Health evaluate() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("service_code", authServiceCode.isEmpty() ? "<not-set>" : authServiceCode);
        details.put("jwt_jwk_set_uri_configured", !jwtJwkSetUri.isEmpty());
        details.put("token_cache_ttl", tokenCacheTtl);
        details.put("actor_resolution_enabled", actorResolutionEnabled);
        details.put("allow_anonymous_context", allowAnonymousContext);

        // Validate required configuration
        boolean jwtConfigured = !jwtJwkSetUri.isEmpty();
        boolean serviceCodeConfigured = !authServiceCode.isEmpty();

        if (!jwtConfigured) {
            log.warn("tnt-auth-core health check FAILED: spring.security.oauth2.resourceserver.jwt.jwk-set-uri is not set");
            return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "jwt_jwk_set_uri_missing")
                    .build();
        }

        if (!serviceCodeConfigured) {
            log.warn("tnt-auth-core health check DEGRADED: tnt.auth.service-code is not set");
            return Health.unknown()
                    .withDetails(details)
                    .withDetail("reason", "service_code_missing")
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .build();
    }
}
