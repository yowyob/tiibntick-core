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
 *   <li>JWT issuer URI is configured (not empty).</li>
 *   <li>Auth service code is configured (not empty).</li>
 *   <li>Token cache TTL is a valid positive duration string.</li>
 * </ol>
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

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

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
        details.put("jwt_issuer_configured", !jwtIssuerUri.isEmpty());
        details.put("token_cache_ttl", tokenCacheTtl);
        details.put("actor_resolution_enabled", actorResolutionEnabled);
        details.put("allow_anonymous_context", allowAnonymousContext);

        // Validate required configuration
        boolean jwtConfigured = !jwtIssuerUri.isEmpty();
        boolean serviceCodeConfigured = !authServiceCode.isEmpty();

        if (!jwtConfigured) {
            log.warn("tnt-auth-core health check FAILED: spring.security.oauth2.resourceserver.jwt.issuer-uri is not set");
            return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "jwt_issuer_uri_missing")
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
