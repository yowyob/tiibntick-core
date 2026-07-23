package com.yowyob.tiibntick.core.platformgateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.JwtOrPlatformScopeAuthorizationManager;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.PlatformApiKeyWebFilter;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.PlatformClientTenantHeaderAuthenticationFilter;
import com.yowyob.tiibntick.core.platformgateway.adapter.in.web.PlatformScopeAuthorizationManager;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuditRecorder;
import com.yowyob.tiibntick.core.platformgateway.application.service.PlatformClientAuthenticationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
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
 * {@code /api/v1/platform/**} is the curated business-module proxy prefix (see design
 * doc §2.6), now consumed by the Market and Link platform backends — it requires either
 * {@code MARKET:*} or {@code LINK:*} (Audit n°7 · #15: previously fell through to
 * {@code .anyExchange().permitAll()}, so ANY authenticated platform client — even one
 * scoped to AUTH/SSO/ONBOARDING only — could reach it with no scope check at all).
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
 * <p>A second, separate chain is also registered here at {@code @Order(11)} —
 * {@link #internalServiceDualAuthSecurityWebFilterChain} — for {@code /api/v1/disputes/**}
 * and {@code /api/sales/orders/**} (Audit n°7 · #4 remediation, 2026-07-18). Unlike the
 * blocks above, it is NOT a "platform backend gateway proxy": those two paths are
 * {@code tnt-dispute-core}'s/{@code tnt-sales-core}'s own native endpoints, and the chain
 * accepts either an end-user JWT or a scoped platform Client-Id/Api-Key call — see that
 * method's javadoc for why.
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
                        .pathMatchers("/api/v1/platform/**").access(new PlatformScopeAuthorizationManager(
                                new PlatformScopeAuthorizationManager.Scope("MARKET", "*"),
                                new PlatformScopeAuthorizationManager.Scope("LINK", "*")))
                        .anyExchange().permitAll())
                .addFilterBefore(platformApiKeyWebFilter(authenticationService, auditRecorder, objectMapper),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    // NOTE: same reasoning as platformApiKeyWebFilter() above — kept a plain object, not a
    // @Bean, so WebFlux does not register it as a global filter.
    private PlatformClientTenantHeaderAuthenticationFilter platformClientTenantHeaderAuthenticationFilter(
            PlatformClientAuthenticationService authenticationService,
            PlatformClientAuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        return new PlatformClientTenantHeaderAuthenticationFilter(authenticationService, auditRecorder, objectMapper);
    }

    /**
     * Dual-auth chain for {@code tnt-dispute-core} and {@code tnt-sales-core}
     * (Audit n°7 · #4 remediation, 2026-07-18) — registered at {@code @Order(11)}, between
     * the platform-gateway chain above and {@code TntSecurityConfig}'s JWT catch-all
     * ({@code @Order(20)}).
     *
     * <p>Unlike the {@code @Order(10)} chain, this one accepts EITHER an end-user JWT
     * (ordinary {@code oauth2ResourceServer}, same converter as the catch-all chain) OR a
     * platform Client-Id/Api-Key call scoped to {@code DISPUTE:*}/{@code SALES:*}
     * ({@link PlatformClientTenantHeaderAuthenticationFilter} — a no-op pass-through when
     * those headers are absent, so ordinary JWT calls are unaffected). Both paths are
     * matched by the same chain because {@code coreBackend/tnt-agency-back-core} calls
     * both server-to-server with no end-user JWT available (Kafka-driven flows have no
     * live user session to forward) — see {@code JwtOrPlatformScopeAuthorizationManager}
     * and {@code docs/auth/platform-client-onboarding-guide.md} §4 for the scope catalogue.
     *
     * <p>{@code tnt-dispute-core}/{@code tnt-sales-core} themselves need no awareness of
     * any of this — both resolve the tenant via the pre-existing
     * {@code @CurrentUser TntUserIdentity} pattern (tnt-auth-core), which reads whichever
     * authentication ends up in the reactive security context, JWT or platform-client
     * alike (see {@code PlatformClientAuthenticationToken}'s two-arg constructor).
     *
     * @param jwtAuthenticationConverter the SAME {@code tntJwtAuthenticationConverter} bean
     *         {@code tnt-bootstrap}'s {@code TntSecurityConfig} defines for its own
     *         {@code @Order(20)} chain — injected by type (a plain Spring Security
     *         framework type), not by a hard compile dependency on tnt-bootstrap, so this
     *         module's layering (L1, below L7 tnt-bootstrap) stays intact.
     */
    @Bean
    @Order(11)
    public SecurityWebFilterChain internalServiceDualAuthSecurityWebFilterChain(
            ServerHttpSecurity http,
            PlatformClientAuthenticationService authenticationService,
            PlatformClientAuditRecorder auditRecorder,
            @Qualifier("platformGatewayObjectMapper") ObjectMapper objectMapper,
            ReactiveJwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/api/v1/disputes/**", "/api/sales/orders/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/api/v1/disputes/**").access(
                                new JwtOrPlatformScopeAuthorizationManager("DISPUTE", "*"))
                        .pathMatchers("/api/sales/orders/**").access(
                                new JwtOrPlatformScopeAuthorizationManager("SALES", "*"))
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterBefore(
                        platformClientTenantHeaderAuthenticationFilter(authenticationService, auditRecorder, objectMapper),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
