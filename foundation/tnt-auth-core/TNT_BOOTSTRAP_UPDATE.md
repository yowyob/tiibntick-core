# Updating `tnt-bootstrap` After Adding `tnt-auth-core`

> **Author:** MANFOUO Braun  
> **Scope:** Re-implementation guidance for `tnt-bootstrap` to integrate `tnt-auth-core`

---

## Context

`tnt-bootstrap` is the **sole executable** module of TiiBnTick Core. It assembles every Core module into a single Spring Boot application context. Adding `tnt-auth-core` requires:

1. A new Maven dependency
2. YAML configuration for `tnt.auth.*` properties
3. Optionally, Swagger/OpenAPI grouping annotation updates

`tnt-bootstrap` contains **no logic** — it is a pure assembly and configuration point.

---

## 1. `pom.xml` — Add the Dependency

```xml
<!-- In tnt-bootstrap/pom.xml — dependencies section -->
<!-- ─── FOUNDATION ────────────────────────────────── -->
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>yow-event-kernel</artifactId>
</dependency>
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>yow-i18n-kernel</artifactId>
</dependency>
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-common-core</artifactId>
</dependency>
<!-- ADD THIS ↓ -->
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-auth-core</artifactId>
</dependency>
```

`tnt-auth-core` transitively pulls in:
- `RT-comops-kernel-core`
- `RT-comops-auth-core`
- `spring-boot-starter-security`
- `spring-boot-starter-webflux`
- `nimbus-jose-jwt`

If `tnt-bootstrap` already declares these, Maven deduplication handles it — no conflicts.

---

## 2. `application.yml` — Add `tnt.auth` Configuration Block

### Minimal (dev profile):

```yaml
# application-dev.yml
tnt:
  auth:
    service-code: TNT_AGENCY
    token-cache-ttl: PT14M
    actor-resolution-enabled: true
    allow-anonymous-context: false
```

### Full production profile:

```yaml
# application-prod.yml
tnt:
  auth:
    service-code: TNT_AGENCY           # Matches the client_id registered in YowAuth0
    token-cache-ttl: PT14M             # Should be < JWT expires_in (typically 15min)
    actor-resolution-enabled: true     # Enriches context with actor profile from tnt-actor-core
    allow-anonymous-context: false     # All /api/** endpoints require authentication

# Kernel security (already required by RT-comops-kernel-core)
iwm:
  security:
    jwt:
      auto-generate-key-pair: false
      private-key-path: /run/secrets/jwt_private_key.pem
      key-id: tnt-core-key-prod
      issuer: https://auth.tiibntick.com
      access-token-ttl: PT15M
```

### Test / CI profile:

```yaml
# application-test.yml
tnt:
  auth:
    service-code: TNT_TEST
    actor-resolution-enabled: false    # No tnt-actor-core in unit tests
    allow-anonymous-context: true      # Relax for integration tests

iwm:
  security:
    jwt:
      auto-generate-key-pair: true     # In-memory RSA key for tests
      key-id: tnt-test-key
      issuer: http://localhost:8080
      access-token-ttl: PT15M
```

---

## 3. `TntBootstrapApplication.java` — No Changes Required

The `@SpringBootApplication` class does not need modification.  
Auto-configuration handles everything via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

```java
@SpringBootApplication
public class TntBootstrapApplication {
    public static void main(String[] args) {
        SpringApplication.run(TntBootstrapApplication.class, args);
    }
}
```

---

## 4. Global CORS / Security Configuration — Review & Align

If `tnt-bootstrap` defines a custom `SecurityWebFilterChain` bean (e.g. for CORS or public paths), ensure it does not conflict with the Kernel's `KernelSecurityConfiguration`.

The Kernel uses `@Order` on its `SecurityWebFilterChain`. The bootstrap config should use a **different order** or extend from the Kernel's config:

```java
// In tnt-bootstrap: TntBootstrapSecurityConfig.java (if needed)
@Configuration
@Order(10)  // Higher value = lower priority → runs AFTER kernel's chain
public class TntBootstrapSecurityConfig {

    @Bean
    public SecurityWebFilterChain publicPathsChain(ServerHttpSecurity http) {
        return http
            .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                "/actuator/health",
                "/actuator/info",
                "/.well-known/**",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            ))
            .authorizeExchange(ex -> ex.anyExchange().permitAll())
            .build();
    }
}
```

---

## 5. `Dockerfile` — No Changes

`tnt-auth-core` has no native binary dependencies (unlike OR-Tools).  
The existing Debian-based `eclipse-temurin:21-jre` image is sufficient.

---

## 6. `docker-compose.yml` — No New Services Required

`tnt-auth-core` adds no new infrastructure (no DB, no cache, no broker).  
The existing services (PostgreSQL, Redis, Kafka) are sufficient.

The Kernel already uses Redis for session token caching (`security-ctx:{jti}` keys).  
`tnt-auth-core` reads from the Kernel's Spring Security context — no additional Redis access.

---

## 7. Health Checks — Optional Addition

If `tnt-bootstrap` exposes a custom `/actuator/health` indicator, consider adding a simple check that validates the JWT key is configured:

```java
// TntAuthHealthIndicator.java in tnt-bootstrap
@Component
public class TntAuthHealthIndicator implements ReactiveHealthIndicator {

    private final UserSessionTokenService tokenService;

    public TntAuthHealthIndicator(UserSessionTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(() -> {
            boolean jwtEnabled = tokenService.isJwtEnabled();
            return jwtEnabled
                ? Health.up().withDetail("jwt", "configured").build()
                : Health.down().withDetail("jwt", "not-configured").build();
        });
    }
}
```

---

## 8. Swagger / OpenAPI — Add Security Scheme

Add Bearer token security scheme to the global OpenAPI configuration in `tnt-bootstrap`:

```java
// In tnt-bootstrap OpenAPI config (e.g. TntOpenApiConfiguration.java)
@Bean
public OpenAPI tntCoreOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("TiiBnTick Core API")
            .version("0.0.1"))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components()
            .addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT issued by YowAuth0 (RT-comops-auth-core). " +
                                 "Obtain via POST /api/auth/login")));
}
```

---

## Complete Checklist

| Task | File | Status |
|------|------|--------|
| Add `tnt-auth-core` dependency | `tnt-bootstrap/pom.xml` | **Required** |
| Add `tnt.auth.*` properties | `application-dev.yml` + `application-prod.yml` | **Required** |
| Add Kernel JWT config (`iwm.security.jwt.*`) | All profiles | **Required** |
| Review SecurityWebFilterChain ordering | `TntBootstrapSecurityConfig.java` | If custom chain exists |
| Add health indicator | `TntAuthHealthIndicator.java` | Optional |
| Add Bearer security scheme to OpenAPI | `TntOpenApiConfiguration.java` | Recommended |
| Dockerfile update | — | Not needed |
| docker-compose.yml update | — | Not needed |

---

## What Happens at Startup

When `tnt-bootstrap` starts with `tnt-auth-core` on the classpath:

1. `TntAuthAutoConfiguration` activates (reactive web application detected)
2. `IYowAuthTntAdapter` bean checked — if `tnt-actor-core` is present, its `ActorCoreYowAuthTntAdapter` is used; otherwise `NoOpYowAuthTntAdapter` is registered
3. `TntSecurityContextService` is created, injecting `UserSessionTokenService` from the Kernel
4. `ReactiveSecurityContextExtractor` is created
5. `TntWebFluxConfiguration` registers `TntCurrentUserArgumentResolver` in WebFlux
6. Any `@CurrentUser`-annotated controller parameters are now resolvable

The entire process is transparent and additive — no existing bean is replaced or broken.
