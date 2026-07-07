# Updating `tnt-bootstrap` After Adding `tnt-roles-core`

> **Author:** MANFOUO Braun  
> **Scope:** Re-implementation guidance for `tnt-bootstrap` to integrate `tnt-roles-core`

---

## Context

`tnt-bootstrap` assembles all Core modules. Adding `tnt-roles-core` requires:
1. A new Maven dependency
2. YAML configuration for `tnt.roles.*`
3. A system tenant ID properly configured
4. Optional: OpenAPI security enrichment with role descriptions

`tnt-bootstrap` adds no new logic — it is purely assembly and configuration.

---

## 1. `pom.xml` — Add the Dependency

```xml
<!-- In tnt-bootstrap/pom.xml -->
<!-- ─── FOUNDATION ────────────────────────────────── -->
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-common-core</artifactId>
</dependency>
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-auth-core</artifactId>
</dependency>
<!-- ADD THIS ↓ -->
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-roles-core</artifactId>
</dependency>
```

`tnt-roles-core` transitively pulls in:
- `RT-comops-roles-core` (Kernel's role engine)
- `spring-boot-starter-aop` (for `TntPermissionAspect`)
- `spring-boot-starter-data-redis-reactive` (for permission cache)

---

## 2. `application.yml` — Add `tnt.roles` Configuration Block

### Minimal (dev profile):

```yaml
# application-dev.yml
tnt:
  auth:
    service-code: TNT_AGENCY
    token-cache-ttl: PT14M
    actor-resolution-enabled: true

  roles:
    system-tenant-id: 00000000-0000-0000-0000-000000000001
    provision-on-startup: true
    permission-cache-ttl-seconds: 300
    aop-enabled: true
```

### Full production profile:

```yaml
# application-prod.yml
tnt:
  roles:
    # Stable UUID identifying the TiiBnTick system tenant.
    # Must be the same across all environments and all deployments.
    # Used to seed global/system-scoped role definitions in the Kernel DB.
    system-tenant-id: ${TNT_SYSTEM_TENANT_ID:00000000-0000-0000-0000-000000000001}

    # Provision TiiBnTick role definitions at startup (idempotent — always safe).
    provision-on-startup: true

    # Redis TTL for resolved permission sets (seconds).
    # Should match the Kernel's ReactivePermissionCache TTL.
    permission-cache-ttl-seconds: 300

    # Enable @RequirePermission AOP enforcement on all annotated service methods.
    aop-enabled: true
```

### Test / CI profile:

```yaml
# application-test.yml
tnt:
  roles:
    system-tenant-id: 00000000-0000-0000-0000-000000000001
    provision-on-startup: false    # skip DB seeding in unit/integration tests
    aop-enabled: false             # disable AOP in test slices (use @MockBean instead)
    permission-cache-ttl-seconds: 0
```

---

## 3. Environment Variable

Add the system tenant ID to the deployment configuration:

```dotenv
# .env.prod
TNT_SYSTEM_TENANT_ID=<stable-uuid-chosen-at-project-init>
```

```yaml
# docker-compose.yml (bootstrap service)
services:
  tnt-core:
    environment:
      TNT_SYSTEM_TENANT_ID: "${TNT_SYSTEM_TENANT_ID}"
```

---

## 4. `TntBootstrapApplication.java` — No Changes Required

Auto-configuration handles everything:
- `TntRolesAutoConfiguration` activates automatically
- `TntRoleInitializationService` hooks into `ApplicationReadyEvent`
- `TntPermissionAspect` is registered as an AOP bean

```java
@SpringBootApplication
public class TntBootstrapApplication {
    public static void main(String[] args) {
        SpringApplication.run(TntBootstrapApplication.class, args);
    }
}
```

---

## 5. AOP Configuration — Enable AspectJ Auto-Proxy

Spring Boot auto-configures AOP when `spring-boot-starter-aop` is on the classpath. Verify it is not accidentally disabled:

```yaml
# Should NOT be in application.yml
# spring.aop.auto=false  ← This would break @RequirePermission
```

If the project uses a custom `@EnableAspectJAutoProxy`, ensure it is present:

```java
// In TntBootstrapApplication or any @Configuration class
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class TntAopConfiguration {}
```

For reactive contexts (`proxyTargetClass = true` is important for Spring WebFlux).

---

## 6. OpenAPI / Swagger — Enrich with Role Descriptions

Update the OpenAPI bean in `tnt-bootstrap` to document the TiiBnTick RBAC model:

```java
// In TntOpenApiConfiguration.java
@Bean
public OpenAPI tntCoreOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("TiiBnTick Core API")
            .version("0.0.1")
            .description("TiiBnTick logistics platform — RBAC protected endpoints"))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components()
            .addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description(buildRoleDoc()))
        );
}

private String buildRoleDoc() {
    StringBuilder sb = new StringBuilder("JWT issued by YowAuth0.\n\nTiiBnTick Roles:\n");
    for (TntRole role : TntRole.values()) {
        sb.append(String.format("- **%s** (%s): %s\n",
            role.code(), role.scopeType().name(), role.label()));
    }
    return sb.toString();
}
```

---

## 7. Redis Configuration — Verify Reactive Redis Is Available

`tnt-roles-core` uses `ReactivePermissionCache` from the Kernel, which requires Redis. Verify `application.yml` includes Redis configuration:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
```

In `docker-compose.yml`, ensure the Redis service is present (already required by `tnt-auth-core`'s session token cache).

---

## 8. Health Check — Verify Role Provisioning

Add a simple actuator check that verifies TiiBnTick roles are present in the DB after startup:

```java
// TntRolesHealthIndicator.java in tnt-bootstrap
@Component
public class TntRolesHealthIndicator implements ReactiveHealthIndicator {

    private final TntRoleDefinitionRegistry registry;

    public TntRolesHealthIndicator(TntRoleDefinitionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Health> health() {
        int defined = registry.size();
        int expected = TntRole.values().length;

        return Mono.fromCallable(() -> defined == expected
            ? Health.up()
                .withDetail("roles_defined", defined)
                .withDetail("roles_expected", expected)
                .build()
            : Health.down()
                .withDetail("roles_defined", defined)
                .withDetail("roles_expected", expected)
                .build()
        );
    }
}
```

---

## 9. Startup Sequence — What Happens

When `tnt-bootstrap` starts with `tnt-roles-core`:

```
1. Spring Boot context loads
2. TntRolesAutoConfiguration activates (reactive web detected)
3. TntRoleDefinitionRegistry created → 9 role definitions loaded in memory
4. KernelRoleProvisioningAdapter created → wired to Kernel's CreateRoleUseCase
5. TntPermissionEvaluator created → wired to Kernel's ReactivePermissionResolver
6. TntRoleService created → wired to Kernel's UserRoleAssignmentRepository + RoleRepository
7. TntPermissionAspect registered as AOP bean (if aop-enabled=true)
8. Application context fully ready → ApplicationReadyEvent fired
9. TntRoleInitializationService.provisionSystemRoles() triggered asynchronously:
   - For each of the 9 TntRole definitions:
     - Check if role already exists for system tenant (existsByCode)
     - If not: call CreateRoleUseCase.createRole(CreateRoleCommand)
     - Log result
10. @RequirePermission annotations on service methods now enforced reactively
```

The provisioning step (9) runs on `Schedulers.boundedElastic()` — it does not block the main application thread or delay HTTP readiness.

---

## Complete Checklist

| Task | File | Status |
|------|------|--------|
| Add `tnt-roles-core` dependency | `tnt-bootstrap/pom.xml` | **Required** |
| Add `tnt.roles.*` properties | `application-dev.yml` + `application-prod.yml` | **Required** |
| Configure `TNT_SYSTEM_TENANT_ID` env var | `.env.prod`, `docker-compose.yml` | **Required** |
| Verify AOP auto-proxy enabled | `TntAopConfiguration.java` | **Required** |
| Verify Redis available | `application.yml` | Already required by auth |
| Enrich OpenAPI with role descriptions | `TntOpenApiConfiguration.java` | Recommended |
| Add health indicator | `TntRolesHealthIndicator.java` | Optional |
| Disable AOP in test profiles | `application-test.yml` | Recommended |
| No Dockerfile changes | — | Not needed |
| No new docker-compose services | — | Not needed |
