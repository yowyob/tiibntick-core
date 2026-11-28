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
import com.yowyob.tiibntick.core.roles.domain.model.PlatformAdminAuthorities;
import com.yowyob.tiibntick.core.roles.domain.model.ScopedTokenRoles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
            "/oauth2/**",
            // UI texts and language list: the login page itself must be translated before any token
            // exists. Only the namespaces allowed by tnt.i18n.public-namespaces are served; the
            // account-language endpoint (/api/v1/me/language) stays on the authenticated chain.
            "/api/v1/i18n/languages",
            "/api/v1/i18n/bundles/**"
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

    // Same property as tnt-roles-core's TntRolesProperties#systemTenantId (SystemTenantConsistencyCheck fails the
    // start-up if they ever disagree).
    @Value("${tnt.roles.system-tenant-id}")
    private String systemTenantId;

    /**
     * Registry of the platform administrators (decisions D-159 to D-175). Optional so that a slice without
     * tnt-roles-core's auto-configuration still builds the converter: TNT_ADMIN then only comes from the owner rule.
     */
    @Autowired(required = false)
    private com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase platformAdmins;

    void setPlatformAdmins(com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase admins) {
        this.platformAdmins = admins;
    }

    /** Decision D-176: confirms the agency/organisation roles of a token against the Core's assignments. */
    @Autowired(required = false)
    private com.yowyob.tiibntick.core.roles.application.port.in.ConfirmScopedRolesUseCase scopedRoles;

    void setScopedRoles(com.yowyob.tiibntick.core.roles.application.port.in.ConfirmScopedRolesUseCase scopedRoles) {
        this.scopedRoles = scopedRoles;
    }

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
                new SimpleGrantedAuthority("ROLE_MEDIATOR"),
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
            // Decision D-176: agency/organisation roles of the token wait for the Core's confirmation (step 5)
            List<String> scopedRoleClaims = new ArrayList<>();

            // 1. Extract TiiBnTick roles → ROLE_* prefixed authorities. Decision D-162: a claim never brings
            //    TNT_ADMIN (a tenant owner can create that code in their own Kernel tenant).
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof List<?> roleList) {
                roleList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(r -> !PlatformAdminAuthorities.isForgeableAdminClaim(r))
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .forEach(role -> keepOrHold(role, authorities, scopedRoleClaims));
            }

            // 2. Extract fine-grained permissions → resource:action[#scope] format
            // These are consumed by TntPermissionEvaluator (tnt-roles-core) fast-path.
            //    Decision D-162: entries that would make the holder a platform administrator (ROLE_TNT_ADMIN…,
            //    "*", a permission only TNT_ADMIN holds or a wildcard covering one) are dropped.
            Object permClaim = jwt.getClaim("permissions");
            boolean ownerOfSystemTenant = false;
            if (permClaim instanceof List<?> permList) {
                permList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(p -> !PlatformAdminAuthorities.isForgeableAdminClaim(p))
                        .forEach(p -> keepOrHold(p, authorities, scopedRoleClaims));

                // Self-service personas granted by the Kernel (ROLE_CLIENT, ROLE_FREELANCER#TENANT:<id>…)
                // become plain ROLE_<CODE> authorities so @PreAuthorize("hasRole(..)") works.
                permList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(TntSecurityConfig::selfServiceRoleAuthority)
                        .flatMap(java.util.Optional::stream)
                        .distinct()
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
                ownerOfSystemTenant = isKernelOwner && ownsSystemTenant;
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

            // 4. Platform administrator (decisions D-159 to D-175): the Kernel owner of the system tenant, or an
            //    account holding an active grant in the Core registry — whatever the tenant of this login. Both
            //    receive the same set (D-167); the owner's protected SYSTEM_OWNER grant is recorded on the way
            //    (D-161), so that a later login in another tenant still finds it.
            final boolean owner = ownerOfSystemTenant;
            UUID subject = uuidOrNull(jwt.getSubject());
            Mono<Boolean> admin;
            if (owner) {
                authorities.add(new SimpleGrantedAuthority(PlatformAdminAuthorities.SYSTEM_TENANT_OWNER_MARKER));
                admin = platformAdmins == null || subject == null ? Mono.just(true)
                        : platformAdmins.ensureSystemOwner(subject).thenReturn(true)
                                .onErrorResume(e -> {
                                    log.warn("SYSTEM_OWNER grant of {} not recorded: {}", subject, e.getMessage());
                                    return Mono.just(true);
                                });
            } else {
                admin = platformAdmins == null || subject == null ? Mono.just(false)
                        : platformAdmins.isPlatformAdmin(subject)
                                .onErrorResume(e -> {
                                    // fail closed: no administrator rights when the registry cannot answer
                                    log.warn("Platform admin registry unavailable for {}: {}", subject, e.getMessage());
                                    return Mono.just(false);
                                });
            }
            // 5. Decision D-176: an agency/organisation role of the token is kept only when the Core's own assignments
            //    confirm it for this account in this tenant (fail closed: none when they cannot be read).
            Mono<java.util.Set<String>> confirmed = scopedRoleClaims.isEmpty() || scopedRoles == null
                    || subject == null || uuidOrNull(tenantId) == null
                    ? Mono.just(java.util.Set.<String>of())
                    : scopedRoles.confirmedScopedRoles(uuidOrNull(tenantId), subject)
                            .defaultIfEmpty(java.util.Set.of())
                            .onErrorResume(e -> {
                                log.warn("Scoped roles of {} not confirmed ({}): dropped", subject, e.getMessage());
                                return Mono.just(java.util.Set.of());
                            });
            return admin.defaultIfEmpty(owner).zipWith(confirmed).flatMapMany(t -> {
                if (t.getT1()) {
                    PlatformAdminAuthorities.AUTHORITIES.stream()
                            .map(SimpleGrantedAuthority::new)
                            .forEach(authorities::add);
                }
                scopedRoleClaims.stream()
                        .filter(claim -> ScopedTokenRoles.confirmed(claim, t.getT2()))
                        .distinct()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
                return Flux.fromIterable(authorities);
            });
        });

        return converter;
    }

    /**
     * Roles any user may legitimately hold in their own tenant just by creating the matching
     * profile. Deliberately a short allow-list: a tenant owner can create and assign arbitrary
     * role codes in their own Kernel tenant, so privileged codes (TNT_ADMIN, AGENCY_MANAGER…)
     * must never be derived from the token alone — they keep the system-tenant check above.
     */
    /** Plain entries become authorities now; agency/organisation roles wait for the confirmation (D-176). */
    private static void keepOrHold(String entry, List<GrantedAuthority> authorities, List<String> held) {
        if (ScopedTokenRoles.entryOf(entry).isPresent()) {
            held.add(entry);
        } else {
            authorities.add(new SimpleGrantedAuthority(entry));
        }
    }

    private static UUID uuidOrNull(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static final java.util.Set<String> SELF_SERVICE_ROLES = java.util.Set.of("CLIENT", "FREELANCER");

    /**
     * Maps a {@code permissions}-claim entry such as {@code ROLE_FREELANCER} or
     * {@code ROLE_FREELANCER#TENANT:<id>} to {@code ROLE_FREELANCER} when it names a
     * self-service role; empty otherwise.
     */
    static java.util.Optional<String> selfServiceRoleAuthority(String permission) {
        if (permission == null || !permission.startsWith("ROLE_")) {
            return java.util.Optional.empty();
        }
        String code = permission.substring("ROLE_".length());
        int hash = code.indexOf('#');
        if (hash >= 0) {
            code = code.substring(0, hash);
        }
        return SELF_SERVICE_ROLES.contains(code) ? java.util.Optional.of("ROLE_" + code) : java.util.Optional.empty();
    }
}
