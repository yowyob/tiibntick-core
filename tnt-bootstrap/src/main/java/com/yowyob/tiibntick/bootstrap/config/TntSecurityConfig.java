package com.yowyob.tiibntick.bootstrap.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.web.server.WebFilter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Reactive Security configuration for TiiBnTick Core.
 *
 * <p>Two ordered {@link SecurityWebFilterChain} beans are registered:
 * <ol>
 *   <li>{@code publicPathsSecurityWebFilterChain} — {@code @Order(5)}: permits health probes,
 *       Swagger UI, WebSocket upgrade and CORS pre-flight without authentication.</li>
 *   <li>{@code authenticatedSecurityWebFilterChain} — {@code @Order(20)}: all {@code /api/**}
 *       paths require a valid JWT. Acts as fallback when the Kernel
 *       ({@code RT-comops-kernel-core}) is absent.</li>
 * </ol>
 *
 * <p><strong>Integration with tnt-auth-core (L1):</strong><br>
 * When the Kernel is present, {@code KernelSecurityConfiguration} (Order ≤ 1) intercepts
 * requests first and sets {@code ApiKeyAuthenticationToken} in the reactive context.
 * {@code TntSecurityContextService} (tnt-auth-core) then builds a {@code TntSecurityContext}
 * which is injected into controllers via the {@code @CurrentUser} annotation.
 *
 * <p><strong>Integration with tnt-roles-core (L1):</strong><br>
 * The {@code @RequirePermission} AOP aspect (enabled by {@link TntAopConfiguration}) intercepts
 * reactive service methods and evaluates permissions via the fast-path
 * ({@code TntPermissionEvaluator} reads JWT authorities — no DB call).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class TntSecurityConfig {

    /**
     * Paths that are always publicly accessible — no JWT required — except
     * {@code /actuator/prometheus} which is dropped from this list in {@code PROD}
     * (Audit n°7 · #16: a metrics endpoint reachable with zero authentication is a
     * free reconnaissance/DoS surface). It still falls through to the {@code @Order(20)}
     * authenticated chain there — production scraping must go through network-level
     * ACLs (Prometheus is only ever reached from inside the cluster) or a scrape
     * credential, never an open HTTP GET.
     */
    private static final List<String> BASE_PUBLIC_PATHS = List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/ws/**",
            // OIDC discovery + OAuth2 token/introspect/userinfo (PlatformAuthOidcController):
            // standards-compliant passthrough to the Kernel — must stay callable by any
            // OIDC/OAuth2 client library without TiiBnTick-specific headers.
            "/.well-known/**",
            "/oauth2/**"
            // NOTE: /api/v1/auth/** and /api/v1/sso/** (platform → Core gateway) are NOT
            // listed here — they're handled by tnt-platform-gateway-core's
            // TntPlatformGatewaySecurityConfig.platformGatewaySecurityWebFilterChain (@Order(10)),
            // which enforces X-Client-Id/X-Api-Key via PlatformApiKeyWebFilter. Adding them to
            // this @Order(5) fully-public chain would short-circuit that check entirely — see
            // docs/auth/platform-client-management-design.md.
    );

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";

    @Value("${tnt.security.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private List<String> allowedOrigins;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

    @Value("${tnt.auth.allow-anonymous-context:false}")
    private boolean allowAnonymousContext;

    @Value("${tnt.auth.dev-tenant-id:43427172-b6ee-4dbf-9148-96682702ffc9}")
    private String devTenantId;

    @Value("${tnt.auth.dev-actor-id:709f0069-c5ed-4d50-8ad0-b6c67a9eb630}")
    private String devActorId;

    // Same property/default as tnt-roles-core's TntRolesProperties#systemTenantId — kept in
    // sync manually since this class lives in tnt-bootstrap and must not depend on tnt-roles-core.
    @Value("${tnt.roles.system-tenant-id:00000000-0000-0000-0000-000000000001}")
    private String systemTenantId;

    @Autowired
    private ApplicationProfile applicationProfile;

    // ── Fail-fast guard (Audit n°7 · #9) ────────────────────────────────────────

    /**
     * Refuses to start when {@code tnt.auth.allow-anonymous-context=true} (dev-mode
     * bypass that injects a synthetic, fully-authenticated {@code ROLE_TNT_ADMIN}
     * principal for every request — see {@link #devAuthFilter()}) is combined with an
     * active {@code PROD} profile. Without this guard a stray/leaked
     * {@code TNT_AUTH_ALLOW_ANONYMOUS=true} env var in production would silently grant
     * every unauthenticated caller full admin rights on every endpoint.
     *
     * <p>Runs as a plain {@code @PostConstruct} so throwing here aborts
     * {@code ApplicationContext.refresh()} itself — the same fail-fast shape used by
     * {@link TntProdSecretsGuard} for Audit n°7 · #8.
     */
    @PostConstruct
    void validateAnonymousContextNotAllowedInProd() {
        if (allowAnonymousContext && applicationProfile.isProduction()) {
            throw new IllegalStateException(
                    "Refusing to start with profile PROD: tnt.auth.allow-anonymous-context=true "
                            + "(TNT_AUTH_ALLOW_ANONYMOUS) would inject a synthetic ROLE_TNT_ADMIN principal "
                            + "into every unauthenticated request. Set TNT_AUTH_ALLOW_ANONYMOUS=false (or unset it) "
                            + "for the prod profile.");
        }
    }

    /**
     * Builds the effective public-path list: {@link #BASE_PUBLIC_PATHS} plus
     * {@code /actuator/prometheus} in every profile except {@code PROD} (Audit n°7 · #16).
     */
    private String[] resolvePublicPaths() {
        List<String> paths = new ArrayList<>(BASE_PUBLIC_PATHS);
        if (!applicationProfile.isProduction()) {
            paths.add(PROMETHEUS_PATH);
        }
        return paths.toArray(new String[0]);
    }

    // ── Chain 1: Public paths ─────────────────────────────────────────────────

    /**
     * Permits the declared public paths without any authentication check.
     * Runs at {@code @Order(5)} — before the Kernel chain and the authenticated chain.
     *
     * @param http reactive HTTP security DSL
     * @return built {@link SecurityWebFilterChain}
     */
    @Bean
    @Order(5)
    public SecurityWebFilterChain publicPathsSecurityWebFilterChain(ServerHttpSecurity http) {
        String[] publicPaths = resolvePublicPaths();
        log.info("Configuring TiiBnTick public-paths security chain (order=5) — {} paths, prometheus public={}",
                publicPaths.length, !applicationProfile.isProduction());
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(publicPaths))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }

    // ── Chain 2: Authenticated paths ──────────────────────────────────────────

    /**
     * Requires a valid JWT for all remaining paths.
     * Runs at {@code @Order(20)} — after the Kernel chain when present.
     *
     * <p>The JWT converter ({@link #tntJwtAuthenticationConverter()}) extracts both
     * {@code roles} and {@code permissions} claims, making them available as Spring
     * {@link GrantedAuthority} objects for {@code @RequirePermission} and
     * {@code @PreAuthorize} evaluation.
     *
     * @param http reactive HTTP security DSL
     * @return built {@link SecurityWebFilterChain}
     */
    @Bean
    @Order(20)
    public SecurityWebFilterChain authenticatedSecurityWebFilterChain(ServerHttpSecurity http) {
        log.info("Configuring TiiBnTick authenticated security chain (order=20) — JWT issuer: {} — anonymous={}",
                jwtIssuerUri, allowAnonymousContext);
        var builder = http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        if (allowAnonymousContext) {
            // Inject a fully-authenticated token so isAuthenticated() SpEL returns true.
            // AnonymousAuthenticationToken would pass hasRole() but fail isAuthenticated().
            return builder
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .addFilterBefore(devAuthFilter(), org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
                    .build();
        }
        return builder
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(tntJwtAuthenticationConverter())))
                .build();
    }

    // ── Dev auth filter ────────────────────────────────────────────────────────

    /**
     * Dev-mode WebFilter: injects a fully-authenticated UsernamePasswordAuthenticationToken
     * so that @PreAuthorize("isAuthenticated()") passes, unlike AnonymousAuthenticationToken.
     */
    private WebFilter devAuthFilter() {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_TNT_ADMIN"),
                new SimpleGrantedAuthority("ROLE_AGENCY_MANAGER"),
                new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER"),
                new SimpleGrantedAuthority("ROLE_ORG_ADMIN"),
                new SimpleGrantedAuthority("ROLE_SUPPORT_AGENT"),
                new SimpleGrantedAuthority("ROLE_PERMANENT_DELIVERER"),
                new SimpleGrantedAuthority("ROLE_FREELANCER"),
                new SimpleGrantedAuthority("ROLE_CLIENT"),
                new SimpleGrantedAuthority("ROLE_RELAY_OPERATOR"),
                new SimpleGrantedAuthority("ROLE_FREELANCER_OWNER"),
                new SimpleGrantedAuthority("ROLE_FREELANCER_SUB"),
                new SimpleGrantedAuthority("ROLE_OWNER"),
                new SimpleGrantedAuthority("accounting:read"),
                new SimpleGrantedAuthority("accounting:write"),
                new SimpleGrantedAuthority("accounting:admin"),
                new SimpleGrantedAuthority("sales:read"),
                new SimpleGrantedAuthority("sales:write"),
                new SimpleGrantedAuthority("tnt:platform:admin"),
                new SimpleGrantedAuthority("administration:permissions:read"),
                new SimpleGrantedAuthority("administration:roles:read"),
                new SimpleGrantedAuthority("administration:roles:write"),
                new SimpleGrantedAuthority("administration:settings:read"),
                new SimpleGrantedAuthority("administration:settings:write"),
                new SimpleGrantedAuthority("TENANT_" + devTenantId),
                new SimpleGrantedAuthority("ACTOR_" + devActorId)
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(devActorId, null, authorities);
        SecurityContextImpl ctx = new SecurityContextImpl(auth);
        return (exchange, chain) -> chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(reactor.core.publisher.Mono.just(ctx)));
    }

    // ── CORS ───────────────────────────────────────────────────────────────────

    /**
     * CORS configuration shared by both chains.
     * Allows the configured origins with full HTTP method set and the TiiBnTick
     * custom request headers ({@code X-Tenant-Id}, {@code X-Request-Id}).
     *
     * @return configured {@link CorsConfigurationSource}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Tenant-Id",
                "X-Request-Id", "Accept", "Origin", "Upgrade", "Connection"));
        config.setExposedHeaders(List.of("X-Total-Count", "X-Request-Id", "Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ── JWT Authorities Converter ──────────────────────────────────────────────

    /**
     * Extracts TiiBnTick business roles AND fine-grained permissions from JWT claims.
     *
     * <p>Claims mapping:
     * <ul>
     *   <li>{@code roles} claim → {@code ROLE_} prefixed authorities
     *       (e.g. {@code ROLE_AGENCY_MANAGER}). Aligns with {@code TntRole} enum codes.</li>
     *   <li>{@code permissions} claim → raw permission strings in
     *       {@code resource:action[#SCOPE]} format (e.g. {@code mission:create#AGENCY:<id>}).
     *       Consumed by {@code TntPermissionEvaluator} fast-path.</li>
     *   <li>{@code actor} claim → {@code ACTOR_<uuid>} synthetic authority for tnt-auth-core
     *       context enrichment.</li>
     *   <li>{@code tid} claim → {@code TENANT_<uuid>} synthetic authority for tenant scoping.</li>
     * </ul>
     *
     * <p>This converter is used by the fallback OAuth2 resource server chain only.
     * When the Kernel's {@code ApiKeyAuthenticationToken} is used, authorities are already
     * set by the Kernel and this converter is not invoked.
     *
     * @return configured {@link ReactiveJwtAuthenticationConverter}
     */
    @Bean
    public ReactiveJwtAuthenticationConverter tntJwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // 1. Extract TiiBnTick roles → ROLE_* prefixed authorities
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof List<?> roleList) {
                roleList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }

            // 2. Extract fine-grained permissions → resource:action[#scope] format
            // These are consumed by TntPermissionEvaluator (tnt-roles-core) fast-path.
            Object permClaim = jwt.getClaim("permissions");
            if (permClaim instanceof List<?> permList) {
                permList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);

                // Kernel OWNER of TiiBnTick's own system tenant → grant all TiiBnTick roles and
                // fine-grained authority strings. ROLE_OWNER/tenant:admin only means "owner of
                // THIS particular Kernel tenant" — it must not be treated as system-wide
                // TNT_ADMIN unless that tenant is TiiBnTick's system tenant, otherwise every
                // self-registered tenant owner on the platform would get full admin rights.
                boolean isKernelOwner = permList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .anyMatch(p -> p.startsWith("ROLE_OWNER") || p.equals("tenant:admin"));
                boolean ownsSystemTenant = systemTenantId.equals(jwt.getClaimAsString("tid"));
                if (isKernelOwner && ownsSystemTenant) {
                    // TiiBnTick business roles (checked via hasRole / @PreAuthorize)
                    List.of("ROLE_TNT_ADMIN", "ROLE_AGENCY_MANAGER", "ROLE_BRANCH_MANAGER",
                            "ROLE_ORG_ADMIN", "ROLE_SUPPORT_AGENT",
                            "ROLE_PERMANENT_DELIVERER", "ROLE_FREELANCER", "ROLE_CLIENT",
                            "ROLE_RELAY_OPERATOR", "ROLE_FREELANCER_OWNER", "ROLE_FREELANCER_SUB")
                            .forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
                    // Fine-grained authority strings (checked via hasAuthority / @PreAuthorize)
                    List.of("accounting:read", "accounting:write", "accounting:admin",
                            "sales:read", "sales:write",
                            "administration:permissions:read",
                            "administration:roles:read", "administration:roles:write",
                            "administration:settings:read", "administration:settings:write",
                            "tnt:platform:admin")
                            .forEach(a -> authorities.add(new SimpleGrantedAuthority(a)));
                }
            }

            // 3. Synthetic authorities for tnt-auth-core TntSecurityContext enrichment
            // These allow TntSecurityContextService to read actor/tenant IDs from authorities
            // without re-parsing the raw JWT, keeping the enrichment lightweight.
            String actorId = jwt.getClaimAsString("actor");
            String tenantId = jwt.getClaimAsString("tid");
            String agencyId = jwt.getClaimAsString("aid");
            String orgId    = jwt.getClaimAsString("oid");

            if (actorId  != null) authorities.add(new SimpleGrantedAuthority("ACTOR_"  + actorId));
            if (tenantId != null) authorities.add(new SimpleGrantedAuthority("TENANT_" + tenantId));
            if (agencyId != null) authorities.add(new SimpleGrantedAuthority("AGENCY_" + agencyId));
            if (orgId    != null) authorities.add(new SimpleGrantedAuthority("ORG_"    + orgId));

            return Flux.fromIterable(authorities);
        });

        return converter;
    }
}
