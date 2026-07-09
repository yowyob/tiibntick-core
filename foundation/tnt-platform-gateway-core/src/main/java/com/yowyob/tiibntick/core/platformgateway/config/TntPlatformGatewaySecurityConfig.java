package com.yowyob.tiibntick.core.platformgateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.PlatformApiKeyWebFilter;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.PlatformScopeAuthorizationManager;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuditRecorder;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Security chain for the platform → Core gateway ({@code /api/v1/auth/**},
 * {@code /api/v1/sso/**}, {@code /api/v1/onboarding/**}, {@code /api/v1/platform/**}) —
 * registered at {@code @Order(10)}, between {@code TntSecurityConfig}'s fully-public
 * chain ({@code @Order(5)}, e.g. actuator/swagger) and its JWT-authenticated catch-all
 * ({@code @Order(20)}).
 *
 * <p>Moved here from {@code tnt-auth-core}'s {@code TntAuthGatewaySecurityConfig}
 * (2026-07-09, see {@code docs/auth/platform-client-management-design.md} §2.0), and
 * extended with real per-block scope authorization (previously
 * {@code .authorizeExchange(ex -> ex.anyExchange().permitAll())} — any authenticated
 * platform client could reach anything behind the chain). Each gateway block now
 * requires its own scope ({@code AUTH:*}, {@code SSO:*}, {@code ONBOARDING:*}), checked
 * via {@link PlatformScopeAuthorizationManager} against the shared {@code PermissionMatcher}
 * — deliberately not the framework's native {@code hasAuthority()} (see that class's
 * javadoc for why).
 *
 * <p>{@code /api/v1/onboarding/**} is implemented in {@code tnt-administration-core}
 * ({@code PlatformAgencyOnboardingController}), not this module — its matcher lives here
 * anyway because this is the one place the whole platform-gateway security perimeter is
 * defined; adding a second, duplicate chain per module would be easy to get out of sync.
 * {@code /api/v1/platform/**} is reserved for future curated business-module proxies
 * (see design doc §2.6) — no scope requirement is pre-registered for it since no such
 * proxy exists yet; it is added to the matcher now so a future proxy controller doesn't
 * also require a security-config change.
 *
 * <p>The admin API ({@code /api/v1/admin/platform-clients/**}, {@code /api/v1/admin/api-keys/**},
 * {@code /api/v1/admin/scope-registry}) is deliberately NOT matched by this chain — it is
 * a human/JWT-authenticated surface, handled by {@code TntSecurityConfig}'s ordinary
 * catch-all chain plus {@code tnt-roles-core}'s {@code @RequirePermission}, not the
 * platform {@code X-Client-Id}/{@code X-Api-Key} mechanism.
 *
 * <p>None of these path prefixes must also appear in {@code TntSecurityConfig.PUBLIC_PATHS}
 * (@Order 5) — that chain is evaluated first and would short-circuit this one, skipping
 * the API-key check entirely.
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class TntPlatformGatewaySecurityConfig {

    // NOTE: intentionally NOT a @Bean. Spring Boot's WebFlux auto-configuration registers
    // every ApplicationContext bean assignable to `WebFilter` as a GLOBAL filter applied to
    // every request, regardless of the declared factory-method return type. Keeping it a
    // plain object built inside the chain method scopes it to this chain's own
    // securityMatcher only (see the equivalent note in the pre-2026-07-09 tnt-auth-core version).
    private PlatformApiKeyWebFilter platformApiKeyWebFilter(
            PlatformClientAuthenticationService authenticationService,
            PlatformClientAuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        return new PlatformApiKeyWebFilter(authenticationService, auditRecorder, objectMapper);
    }

    @Bean
    @Order(10)
    public SecurityWebFilterChain platformGatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            PlatformClientAuthenticationService authenticationService,
            PlatformClientAuditRecorder auditRecorder,
            @Qualifier("platformGatewayObjectMapper") ObjectMapper objectMapper,
            CorsConfigurationSource corsConfigurationSource) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/api/v1/auth/**", "/api/v1/sso/**", "/api/v1/onboarding/**", "/api/v1/platform/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/v1/auth/**").access(new PlatformScopeAuthorizationManager("AUTH", "*"))
                        .pathMatchers("/api/v1/sso/**").access(new PlatformScopeAuthorizationManager("SSO", "*"))
                        .pathMatchers("/api/v1/onboarding/**").access(new PlatformScopeAuthorizationManager("ONBOARDING", "*"))
                        .anyExchange().permitAll())
                .addFilterBefore(platformApiKeyWebFilter(authenticationService, auditRecorder, objectMapper),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
