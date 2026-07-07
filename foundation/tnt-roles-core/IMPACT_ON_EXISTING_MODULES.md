# Impact of Adding `tnt-roles-core` on Existing Core Modules

> **Author:** MANFOUO Braun  
> **Context:** Adding `tnt-roles-core` to the FOUNDATION layer (L1) of TiiBnTick Core

---

## 0. Parent POM — Mandatory Change

Add `tnt-roles-core` in the `<modules>` section of the root `pom.xml`, **after** `tnt-auth-core`:

```xml
<modules>
    <!-- COUCHE L0 — FOUNDATION -->
    <module>foundation/yow-event-kernel</module>
    <module>foundation/yow-i18n-kernel</module>
    <module>foundation/tnt-common-core</module>
    <module>foundation/tnt-auth-core</module>
    <module>foundation/tnt-roles-core</module>   <!-- ADD THIS -->

    <!-- COUCHE L2 — IDENTITY -->
    <module>identity/tnt-actor-core</module>
    ...
</modules>
```

Add to `<dependencyManagement>`:

```xml
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-roles-core</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 1. `tnt-common-core` — No Changes Required

`tnt-roles-core` depends on `tnt-common-core`, not the reverse. No changes.

---

## 2. `tnt-auth-core` — Use `tnt-roles-core` for Permission Enrichment (Recommended)

`tnt-auth-core` can optionally enrich `TntSecurityContext` with role information from `tnt-roles-core`. Since `tnt-auth-core` is in the same FOUNDATION layer, the dependency direction must be:

**`tnt-auth-core` → `tnt-roles-core`** (auth imports roles, not the reverse — avoids circular dep).

Add to `tnt-auth-core/pom.xml`:

```xml
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-roles-core</artifactId>
</dependency>
```

`TntSecurityContextService` can then use `TntRole.isKnownRole(authority)` to split authorities into `roles` (known TiiBnTick roles) vs `permissions` (raw strings):

```java
// In TntSecurityContextService.buildContext()
Set<String> roles = allAuthorities.stream()
    .filter(a -> TntRole.isKnownRole(a.replace("ROLE_", "")))
    .map(a -> a.startsWith("ROLE_") ? a : "ROLE_" + a)
    .collect(Collectors.toUnmodifiableSet());

Set<String> permissions = allAuthorities.stream()
    .filter(a -> !TntRole.isKnownRole(a.replace("ROLE_", "")) && !a.startsWith("ROLE_"))
    .collect(Collectors.toUnmodifiableSet());
```

This change is **optional but recommended** — it makes the `TntSecurityContext.roles()` set properly typed to TiiBnTick business roles.

---

## 3. `tnt-administration-core` — Use `TntRoleDefinitionRegistry` for Tenant Provisioning (Required)

`tnt-administration-core` manages role lifecycle. With `tnt-roles-core` it can:
1. Use `TntRoleDefinitionRegistry.getAllDefinitions()` as templates when onboarding a new tenant
2. Call `TntRoleInitializationService.provisionForTenant(tenantId)` at organization creation
3. Use `TntPermission.*` constants in its own permission catalog

Add to `tnt-administration-core/pom.xml`:

```xml
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-roles-core</artifactId>
</dependency>
```

In the organization onboarding use case:

```java
// In tnt-administration-core: OrganizationOnboardingService
@Autowired
private TntRoleInitializationService tntRoleInitializationService;

public Mono<Void> onboardTenant(UUID tenantId) {
    return tntRoleInitializationService.provisionForTenant(tenantId)
        .then(provisionDefaultSettings(tenantId))
        .then(publishOnboardingEvent(tenantId));
}
```

Also update the `PermissionCatalogService` (in `tnt-administration-core`) to include all `TntPermission.*` constants, sourcing them from `tnt-roles-core`.

---

## 4. All Business Service Modules — Use `@RequirePermission` (Recommended)

Every module that exposes business operations benefits from declarative permission enforcement.

**Pattern to adopt (each module):**

Add dependency:
```xml
<dependency>
    <groupId>com.yowyob.tiibntick.core</groupId>
    <artifactId>tnt-roles-core</artifactId>
</dependency>
```

Replace manual permission checks with `@RequirePermission`:

```java
// BEFORE (manual, repetitive)
public Mono<Mission> createMission(CreateMissionCommand cmd) {
    return extractor.requirePermission("mission", "create")
        .flatMap(ctx -> doCreate(cmd, ctx));
}

// AFTER (declarative, clean)
@RequirePermission(resource = "mission", action = "create")
public Mono<Mission> createMission(CreateMissionCommand cmd) {
    return doCreate(cmd);
}
```

---

## 5. `tnt-delivery-core` — Key Integration Point

The delivery module handles mission and parcel lifecycle — all its operations map directly to TiiBnTick permissions.

```java
// DeliveryService in tnt-delivery-core
@RequirePermission(resource = "mission", action = "create")
public Mono<Mission> createMission(CreateMissionCommand cmd) { ... }

@RequirePermission(resource = "mission", action = "assign")
public Mono<Void> assignDeliverer(UUID missionId, UUID delivererId) { ... }

@RequirePermission(resource = "delivery", action = "confirm")
public Mono<Void> confirmDelivery(UUID deliveryId, DeliveryProof proof) { ... }
```

No structural changes — only `@RequirePermission` annotations added to existing methods.

---

## 6. `tnt-billing-*` Modules — Protect Financial Operations

```java
// In tnt-billing-invoice
@RequirePermission(resource = "invoice", action = "issue")
public Mono<Invoice> issueInvoice(IssueInvoiceCommand cmd) { ... }

// In tnt-billing-wallet
@RequirePermission(resource = "payment", action = "process")
public Mono<Transaction> processPayment(PaymentCommand cmd) { ... }

@RequirePermission(resource = "payment", action = "refund")
public Mono<Refund> refundPayment(RefundCommand cmd) { ... }

// In tnt-billing-report
@RequirePermission(resource = "report", action = "export")
public Flux<ReportLine> exportReport(ReportFilter filter) { ... }
```

---

## 7. `tnt-actor-core` — Role-Based Actor Enrichment

After role resolution, `tnt-actor-core` can now determine the actor type from their TiiBnTick role:

```java
// In ActorProfileService
public Mono<ActorType> resolveActorType(UUID userId, UUID tenantId) {
    return tntRoleService.resolveHighestRole(userId, tenantId)
        .map(role -> switch (role) {
            case PERMANENT_DELIVERER -> ActorType.PERMANENT_DELIVERER;
            case FREELANCER          -> ActorType.FREELANCER;
            case RELAY_OPERATOR      -> ActorType.RELAY_OPERATOR;
            case CLIENT              -> ActorType.CLIENT;
            default                  -> ActorType.ADMINISTRATIVE;
        })
        .defaultIfEmpty(ActorType.UNKNOWN);
}
```

---

## 8. `tnt-notify-core` — Role-Aware Notification Routing

```java
// Use roles to determine notification channel preference
tntRoleService.resolveHighestRole(userId, tenantId)
    .map(role -> role == TntRole.CLIENT ? NotificationChannel.WHATSAPP : NotificationChannel.IN_APP)
    .flatMap(channel -> notificationDispatcher.send(notification, channel));
```

---

## Summary Table

| Module | Change | Priority |
|--------|--------|----------|
| **Parent `pom.xml`** | Add `<module>` + `<dependencyManagement>` | **MANDATORY** |
| `tnt-auth-core` | Import `tnt-roles-core`, use `TntRole.isKnownRole()` in context builder | RECOMMENDED |
| **`tnt-administration-core`** | Use `TntRoleDefinitionRegistry` + call `provisionForTenant()` | **MANDATORY** |
| `tnt-delivery-core` | Add `@RequirePermission` on service methods | RECOMMENDED |
| `tnt-billing-invoice` | Add `@RequirePermission` on invoice/payment methods | RECOMMENDED |
| `tnt-billing-wallet` | Add `@RequirePermission` on payment/refund methods | RECOMMENDED |
| `tnt-billing-report` | Add `@RequirePermission` on export methods | RECOMMENDED |
| `tnt-actor-core` | Use `TntRoleService.resolveHighestRole()` for actor type | OPTIONAL |
| `tnt-notify-core` | Use roles for channel routing | OPTIONAL |
| **`tnt-bootstrap`** | Add dependency + configure `tnt.roles.*` | **MANDATORY** |

---

## Breaking Changes

**None.** All changes are purely additive. Existing business logic continues to work without modification. `@RequirePermission` annotations are applied incrementally method by method.

The only mandatory items are the parent POM registration, `tnt-administration-core` provisioning integration, and `tnt-bootstrap` configuration.

---

## Dependency Direction Rules (Anti-Corruption)

```
tnt-common-core
      ↑
tnt-auth-core ──────→ tnt-roles-core
                              ↑
                    tnt-administration-core
                              ↑
              tnt-delivery-core, tnt-billing-*, tnt-actor-core, ...
```

**Rule:** `tnt-roles-core` must NEVER depend on `tnt-actor-core`, `tnt-organization-core`, or any L2+ module. The dependency always flows upward (higher layers depend on lower layers).
