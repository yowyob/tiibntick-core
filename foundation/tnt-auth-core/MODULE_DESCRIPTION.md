# tnt-auth-core — Module Description

> **Author:** MANFOUO Braun  
> **Layer:** FOUNDATION (L1) — `foundation/tnt-auth-core`  
> **GroupId:** `com.yowyob.tiibntick.core`  
> **ArtifactId:** `tnt-auth-core`  
> **Role:** Thin adapter between TiiBnTick Core and YowAuth0 (RT-comops-auth-core)

---

## What This Module Does

`tnt-auth-core` is the **security bridge** of TiiBnTick Core. It contains **no authentication logic** of its own. Every cryptographic operation (JWT signing, verification, login, MFA, password reset) lives exclusively in the Kernel (`RT-comops-auth-core` / `RT-comops-kernel-core`).

This module's sole responsibility is translating the Kernel's authentication token (`ApiKeyAuthenticationToken`) into a TiiBnTick-enriched security context (`TntSecurityContext`), and making it ergonomically available throughout the Core modules.

---

## What It Provides

### 1. `TntSecurityContext` (Domain Model)
A clean, Kernel-decoupled record carrying:

| Field | Source |
|-------|--------|
| `userId` | JWT subject (via Kernel) |
| `tenantId` | JWT claim `tid` |
| `actorId` | JWT claim `actor` |
| `organizationId` | JWT claim `oid` |
| `agencyId` | JWT claim `aid` |
| `roles` | Granted authorities prefixed `ROLE_` |
| `permissions` | Granted authorities without `ROLE_` prefix |
| `authenticated` | `Authentication.isAuthenticated()` |
| `freelancer` | Resolved via `IYowAuthTntAdapter` (tnt-actor-core) |
| `clientApplicationId` | Kernel token's `clientApplicationId` |

Methods: `hasPermission(resource, action)`, `hasRole(role)`, `isFullyAuthenticated()`, `hasActorProfile()`, `anonymous()`.

---

### 2. `TntUserIdentity` (Domain Model)
Lightweight projection of `TntSecurityContext` carrying only identity fields.  
Injected via `@CurrentUser` in WebFlux controllers.

---

### 3. `TntTokenClaims` / `TntTokenPair` (Value Objects)
Clean domain wrappers over the Kernel's token types.  
Used for service-to-service token validation without leaking Kernel classes into business modules.

---

### 4. `@CurrentUser` Annotation + `TntCurrentUserArgumentResolver`
Enables clean controller parameters:

```java
@GetMapping("/missions")
public Flux<MissionView> listMissions(@CurrentUser TntUserIdentity currentUser) {
    return missionService.findByActor(currentUser.actorId(), currentUser.tenantId());
}

@GetMapping("/profile")
public Mono<ProfileView> myProfile(@CurrentUser TntSecurityContext ctx) {
    return profileService.find(ctx.userId(), ctx.tenantId());
}
```

- `required = true` (default): throws `TntAuthException.missingContext()` if unauthenticated.
- `required = false`: injects anonymous context or null without error.

---

### 5. `ReactiveSecurityContextExtractor`
Programmatic access for services and event handlers outside controllers:

```java
// Inject anywhere as a Spring bean
@Autowired
private ReactiveSecurityContextExtractor extractor;

public Mono<Mission> createMission(CreateMissionCommand cmd) {
    return extractor.requireActorId()
        .flatMap(actorId -> missionRepository.save(buildMission(cmd, actorId)));
}

// With permission check
public Mono<Report> generate(ReportRequest req) {
    return extractor.requirePermission("report", "generate")
        .flatMap(ctx -> reportEngine.run(req, ctx.tenantId()));
}
```

---

### 6. `IYowAuthTntAdapter` (Outbound Port)
Defined in `tnt-auth-core`, implemented by `tnt-actor-core`.  
Allows actor profile resolution without creating circular dependencies.

```java
public interface IYowAuthTntAdapter {
    Mono<Optional<UUID>> resolveActorId(UUID userId, UUID tenantId);
    Mono<Boolean> isFreelancer(UUID actorId, UUID tenantId);
    Mono<Optional<UUID>> resolveAgencyId(UUID actorId, UUID tenantId);
}
```

When `tnt-actor-core` is absent, `NoOpYowAuthTntAdapter` is auto-registered (returns empty/false).

---

### 7. `TntAuthAutoConfiguration` + `TntWebFluxConfiguration`
Spring Boot auto-configuration that wires all beans conditionally.  
Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

---

### 8. `TntAuthProperties`
Configuration bound from `tnt.auth.*`:

```yaml
tnt:
  auth:
    service-code: TNT_AGENCY
    token-cache-ttl: PT14M
    actor-resolution-enabled: true
    allow-anonymous-context: false
```

---

## What This Module Does NOT Do

| NOT in tnt-auth-core | Where it lives |
|---------------------|----------------|
| User registration | `RT-comops-auth-core` / YowAuth0 |
| Password management | `RT-comops-auth-core` |
| 2FA / MFA | `RT-comops-auth-core` |
| JWT signing | `RT-comops-kernel-core` → `JwtTokenService` |
| Security filter chain | `RT-comops-kernel-core` → `KernelSecurityConfiguration` |
| RBAC permission storage | `tnt-administration-core` |
| Actor profile storage | `tnt-actor-core` |
| Token issuance | `RT-comops-kernel-core` → `UserSessionTokenService` |

---

## Hexagonal Architecture Layout

```
tnt-auth-core/
├── domain/
│   ├── model/
│   │   ├── TntSecurityContext.java      ← Core domain record
│   │   ├── TntUserIdentity.java         ← Lightweight projection
│   │   ├── TntTokenClaims.java          ← Token value object
│   │   └── TntTokenPair.java            ← Token pair value object
│   └── exception/
│       ├── TntAuthException.java        ← Root domain exception
│       └── TntUnauthorizedException.java
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── ResolveCurrentUserUseCase.java   ← Primary port
│   │   │   └── ValidateTokenUseCase.java         ← Primary port
│   │   └── out/
│   │       └── IYowAuthTntAdapter.java           ← Secondary port
│   └── service/
│       └── TntSecurityContextService.java        ← Implements both in-ports
├── adapter/
│   ├── in/web/
│   │   ├── CurrentUser.java                      ← Annotation
│   │   ├── TntCurrentUserArgumentResolver.java   ← WebFlux resolver
│   │   └── ReactiveSecurityContextExtractor.java ← Programmatic access
│   └── out/kernel/
│       └── NoOpYowAuthTntAdapter.java             ← Fallback adapter
└── config/
    ├── TntAuthProperties.java
    ├── TntAuthAutoConfiguration.java
    └── TntWebFluxConfiguration.java
```

---

## Dependencies

| Dependency | Reason |
|------------|--------|
| `RT-comops-kernel-core` | `ApiKeyAuthenticationToken`, `UserSessionTokenService`, `UserSessionTokenClaims` |
| `RT-comops-auth-core` | Kernel auth module present on classpath |
| `tnt-common-core` | Shared TiiBnTick types |
| `spring-boot-starter-webflux` | Reactive web, `HandlerMethodArgumentResolver` |
| `spring-boot-starter-security` | `ReactiveSecurityContextHolder` |
| `nimbus-jose-jwt` | JWT types (transitive via kernel) |

---

## Integration Flow

```
HTTP Request (Bearer JWT)
        ↓
[Kernel KernelSecurityConfiguration]
  ApiKeyServerAuthenticationConverter.convert()
  UserSessionTokenService.verify(token)
  → ApiKeyAuthenticationToken set in ReactiveSecurityContextHolder
        ↓
[tnt-auth-core TntSecurityContextService]
  buildContext(ApiKeyAuthenticationToken)
  + IYowAuthTntAdapter.isFreelancer() / resolveAgencyId()  [optional enrichment]
  → TntSecurityContext
        ↓
[@CurrentUser / ReactiveSecurityContextExtractor]
  → Injected into TiiBnTick Core business services
```
