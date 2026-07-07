# Impact of Adding `tnt-auth-core` on Existing Core Modules

> **Author:** MANFOUO Braun  
> **Context:** Adding `tnt-auth-core` to the FOUNDATION layer (L1) of TiiBnTick Core

---

## 0. Parent POM — Mandatory Change

Add `tnt-auth-core` to the `<modules>` section of the parent `pom.xml`, **after** `tnt-common-core` and **before** the identity modules:

```xml
<modules>
    <!-- COUCHE L0 — FOUNDATION -->
    <module>foundation/yow-event-kernel</module>
    <module>foundation/yow-i18n-kernel</module>
    <module>foundation/tnt-common-core</module>
    <module>foundation/tnt-auth-core</module>   <!-- ADD THIS -->

    <!-- COUCHE L2 — IDENTITY -->
    <module>identity/tnt-actor-core</module>
    ...
</modules>
```

Also add it to `<dependencyManagement>` so other modules can reference it without a version:

```xml
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-auth-core</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 1. `tnt-common-core` — No Changes Required

`tnt-auth-core` depends on `tnt-common-core`, not the other way around.  
No changes needed in `tnt-common-core`.

---

## 2. `tnt-actor-core` — Implement `IYowAuthTntAdapter` (Required)

`tnt-actor-core` must implement the outbound port `IYowAuthTntAdapter` defined in `tnt-auth-core`.  
This closes the loop: auth core knows **who** is authenticated (userId), actor core knows **what actor profile** is linked.

### Add dependency:

```xml
<!-- In tnt-actor-core/pom.xml -->
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-auth-core</artifactId>
</dependency>
```

### Implement the adapter:

```java
package com.yowyob.tiibntick.actor.adapter.out.auth;

import com.yowyob.tiibntick.core.auth.application.port.out.IYowAuthTntAdapter;
// ...

@Component
@ConditionalOnMissingBean(name = "noOpYowAuthTntAdapter")  // replaces the no-op
public class ActorCoreYowAuthTntAdapter implements IYowAuthTntAdapter {

    private final DelivererProfileRepository delivererRepository;
    private final FreelancerProfileRepository freelancerRepository;

    @Override
    public Mono<Optional<UUID>> resolveActorId(UUID userId, UUID tenantId) {
        // Query actor profile linked to this userId
        return delivererRepository.findActorIdByUserId(userId, tenantId)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Boolean> isFreelancer(UUID actorId, UUID tenantId) {
        return freelancerRepository.existsByActorId(actorId, tenantId);
    }

    @Override
    public Mono<Optional<UUID>> resolveAgencyId(UUID actorId, UUID tenantId) {
        return delivererRepository.findAgencyIdByActorId(actorId, tenantId)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
    }
}
```

### Use `@CurrentUser` in actor controllers:

```java
// BEFORE (manual extraction)
public Mono<DelivererView> myProfile(ServerWebExchange exchange) {
    return ReactiveSecurityContextHolder.getContext()
        .map(ctx -> (ApiKeyAuthenticationToken) ctx.getAuthentication())
        .flatMap(token -> delivererService.find(token.actorId(), token.tenantId()));
}

// AFTER (clean injection via tnt-auth-core)
public Mono<DelivererView> myProfile(@CurrentUser TntUserIdentity me) {
    return delivererService.find(me.actorId(), me.tenantId());
}
```

---

## 3. `tnt-organization-core` — Add Dependency, Use `@CurrentUser`

Add `tnt-auth-core` as a compile dependency:

```xml
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-auth-core</artifactId>
</dependency>
```

Replace any manual `ReactiveSecurityContextHolder` usage in controllers and services with:
- `@CurrentUser TntUserIdentity` in controller parameters
- `ReactiveSecurityContextExtractor.requireTenantId()` in service layer

No structural changes required — only ergonomic improvement.

---

## 4. `tnt-tp-core` — Same as tnt-organization-core

Add dependency, replace manual security context reads with `@CurrentUser` / extractor.  
No structural changes.

---

## 5. `tnt-administration-core` — Add Dependency, Permission Checks

Add `tnt-auth-core` dependency.  
`tnt-administration-core` manages RBAC rules — it can now check permissions declaratively:

```java
// Service layer
return extractor.requirePermission("admin", "manage_roles")
    .flatMap(ctx -> roleAssignmentService.assign(cmd, ctx.tenantId()));
```

No structural changes. The RBAC data itself stays in `tnt-administration-core`.

---

## 6. All Logistics Modules (`tnt-geo-core`, `tnt-route-core`, `tnt-delivery-core`, etc.)

These modules often need the current tenant or actor for scoping queries.  
**Pattern to adopt** (no breaking changes, only improvements):

```xml
<!-- Add to each logistics module pom.xml -->
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-auth-core</artifactId>
</dependency>
```

Use `ReactiveSecurityContextExtractor` in service layer for tenant scoping:

```java
// tnt-delivery-core: DeliveryService
public Mono<Mission> createMission(CreateMissionCommand cmd) {
    return extractor.requireActorId()
        .zipWith(extractor.requireTenantId())
        .flatMap(tuple -> missionRepository.save(
            Mission.create(cmd, tuple.getT1(), tuple.getT2())
        ));
}
```

---

## 7. All Business Modules (`tnt-billing-*`, `tnt-accounting-core`, etc.)

Same pattern: add dependency, use extractor for tenant/actor scoping.  
The billing modules in particular benefit from `extractor.requirePermission("billing", "read")` guards.

---

## Summary Table

| Module | Change | Priority |
|--------|--------|----------|
| **Parent `pom.xml`** | Add `<module>` + `<dependencyManagement>` entry | **MANDATORY** |
| **`tnt-actor-core`** | Implement `IYowAuthTntAdapter`, add dependency | **MANDATORY** |
| `tnt-organization-core` | Add dependency, refactor controllers | RECOMMENDED |
| `tnt-tp-core` | Add dependency, refactor controllers | RECOMMENDED |
| `tnt-administration-core` | Add dependency, add permission guards | RECOMMENDED |
| `tnt-delivery-core` | Add dependency, use extractor in services | RECOMMENDED |
| All other L3/L4/L5 modules | Add dependency as needed | OPTIONAL |
| **`tnt-bootstrap`** | Add dependency + configure `tnt.auth.*` | **MANDATORY** |

---

## Breaking Changes

**None.** All existing code that reads directly from `ReactiveSecurityContextHolder` continues to work.  
`tnt-auth-core` provides an **additive** API — migration is incremental and optional per module.

The only mandatory changes are:
1. Parent `pom.xml` module registration
2. `tnt-actor-core` implements `IYowAuthTntAdapter` (otherwise actor-profile enrichment is no-op)
3. `tnt-bootstrap` adds the dependency and YAML config
