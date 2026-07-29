package com.yowyob.tiibntick.core.gofreelancer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Go-Freelancer-Point module-level security configuration.
 *
 * <p>Registers a {@link SecurityWebFilterChain} at {@code @Order(8)} — after the
 * global public-paths chain (@Order 5) but before the authenticated chain (@Order 20)
 * from {@code TntSecurityConfig} in {@code tnt-bootstrap}.
 *
 * <p><strong>Public paths (no JWT required):</strong>
 * <ul>
 *   <li>{@code POST /api/auth/login} — credential authentication</li>
 *   <li>{@code POST /api/auth/register} — new user/client registration</li>
 *   <li>{@code POST /api/auth/refresh} — token renewal</li>
 *   <li>{@code POST /api/auth/setup-password} — first-time password setup (token link)</li>
 *   <li>{@code POST /api/auth/request-password-reset} — request reset email</li>
 *   <li>{@code POST /api/freelancers/register} — freelancer onboarding with documents</li>
 *   <li>{@code GET  /uploads/**} — static file serving (photos already stored)</li>
 * </ul>
 *
 * <p><strong>Protected paths (JWT required):</strong>
 * All other {@code /api/**} paths fall through to the global authenticated chain.
 *
 * @author François-Charles ATANGA
 */
@Configuration
@EnableWebFluxSecurity
public class GofpSecurityConfig {

    private static final String[] GOFP_PUBLIC_PATHS = {
            // Auth — credential endpoints (no token needed to log in or register)
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/setup-password",
            "/api/auth/request-password-reset",
            // Freelancer onboarding — multipart registration (includes KYC docs)
            "/api/freelancers/register",
            // Static files — uploaded images served without auth
            "/uploads/**",
    };

    /**
     * Permits the Go-Freelancer-Point public paths without JWT.
     * Only matches the exact paths listed above — everything else falls through to
     * the {@code @Order(20)} authenticated chain from {@code TntSecurityConfig}.
     *
     * @param http reactive HTTP security DSL
     * @return built {@link SecurityWebFilterChain}
     */
    @Bean
    @Order(8)
    public SecurityWebFilterChain gofpPublicPathsChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(GOFP_PUBLIC_PATHS))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(ex -> ex
                        // Always allow CORS pre-flight
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyExchange().permitAll()
                )
                .build();
    }
}
