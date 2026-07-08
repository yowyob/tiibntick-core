package com.yowyob.tiibntick.core.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.auth.adapter.in.web.PlatformApiKeyWebFilter;
import com.yowyob.tiibntick.core.auth.application.service.PlatformClientRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 * {@code /api/v1/sso/**}, {@code /api/v1/onboarding/**}) — registered at {@code @Order(10)},
 * between {@code TntSecurityConfig}'s fully-public chain ({@code @Order(5)}, e.g.
 * actuator/swagger) and its JWT-authenticated catch-all ({@code @Order(20)}).
 *
 * <p>These paths carry NO end-user JWT requirement at the Core security-chain level —
 * that's the whole point, platforms call them to obtain one, or (for onboarding) the
 * TiiBnTick org/agency doesn't exist yet so no TiiBnTick RBAC permission could apply —
 * but every request must present a valid platform {@code X-Client-Id}/{@code X-Api-Key}
 * pair ({@link PlatformApiKeyWebFilter}), and any {@code Authorization} header the caller
 * supplies is forwarded transparently to the Kernel by the controllers, which remains
 * the sole authority on whether it's valid.
 *
 * <p>{@code /api/v1/onboarding/**} is implemented in {@code tnt-administration-core}
 * ({@code PlatformAgencyOnboardingController}), not tnt-auth-core — its matcher lives
 * here anyway because this is the one place the whole platform-gateway security perimeter
 * is defined; adding a second, duplicate chain per module would be easy to get out of sync.
 *
 * <p>None of these path prefixes must also appear in {@code TntSecurityConfig.PUBLIC_PATHS}
 * (@Order 5) — that chain is evaluated first and would short-circuit this one, skipping the
 * API-key check entirely.
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@EnableConfigurationProperties(TntPlatformGatewayProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class TntAuthGatewaySecurityConfig {

    @Bean
    @ConditionalOnMissingBean(PlatformClientRegistry.class)
    public PlatformClientRegistry platformClientRegistry(TntPlatformGatewayProperties properties) {
        return new PlatformClientRegistry(properties);
    }

    // NOTE: intentionally NOT a @Bean. Spring Boot's WebFlux auto-configuration
    // registers every ApplicationContext bean assignable to `WebFilter` as a GLOBAL
    // filter applied to every request, regardless of the declared factory-method
    // return type. A previous version of this class exposed this as a `@Bean`
    // purely so it could be `.addFilterBefore()`'d into the chain below — but that
    // also made it run on every path handled by the whole app (swagger-ui,
    // actuator, ...), rejecting them with 401 for missing X-Client-Id/X-Api-Key.
    // Keeping it a plain object built inside the chain method scopes it to this
    // chain's own securityMatcher only.
    private PlatformApiKeyWebFilter platformApiKeyWebFilter(
            PlatformClientRegistry registry,
            ObjectMapper objectMapper) {
        return new PlatformApiKeyWebFilter(registry, objectMapper);
    }

    @Bean
    @Order(10)
    public SecurityWebFilterChain platformGatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            PlatformClientRegistry platformClientRegistry,
            @Qualifier("tntAuthObjectMapper") ObjectMapper objectMapper,
            CorsConfigurationSource corsConfigurationSource) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/api/v1/auth/**", "/api/v1/sso/**", "/api/v1/onboarding/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .addFilterBefore(platformApiKeyWebFilter(platformClientRegistry, objectMapper),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
