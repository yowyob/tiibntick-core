# tnt-roles-core — Module Description

> **Author:** MANFOUO Braun  
> **Layer:** FOUNDATION (L1) — `foundation/tnt-roles-core`  
> **GroupId:** `com.yowyob.tiibntick.core`  
> **ArtifactId:** `tnt-roles-core`  
> **Role:** TiiBnTick RBAC extension over RT-comops-roles-core (Kernel)

---

## What This Module Does

`tnt-roles-core` is the **TiiBnTick RBAC vocabulary and DSL layer**. It centralizes every business role and permission specific to TiiBnTick and all its sub-platforms (TiiBnTick Link, Go, Agency, Point, Freelancer, Market) without duplicating a single line of the Kernel's role-management logic.

The Kernel (`RT-comops-roles-core`) already owns: `Role` entity, `UserRoleAssignment`, `RoleRepository`, `UserRoleAssignmentRepository`, `RolesPermissionResolver` (with Redis cache), and `CreateRoleUseCase`. `tnt-roles-core` uses all of these as-is, injecting TiiBnTick-specific data and wrapping them with TiiBnTick-specific semantics.

---

## What It Provides

### 1. `TntRole` Enum — All TiiBnTick Business Roles

| Role Code | Scope | Description |
|-----------|-------|-------------|
| `AGENCY_MANAGER` | AGENCY | Full agency management: staff, missions, billing, reports |
| `BRANCH_MANAGER` | AGENCY | Daily operations of an antenne (branch) |
| `PERMANENT_DELIVERER` | AGENCY | Salaried deliverer attached to an agency |
| `FREELANCER` | TENANT | Independent deliverer responding to announcements |
| `RELAY_OPERATOR` | AGENCY | Hub/relay point operator |
| `CLIENT` | TENANT | End client — announces, tracks, pays |
| `SUPPORT_AGENT` | TENANT | Customer support — read-only + dispute resolution |
| `ORG_ADMIN` | ORGANIZATION | Multi-agency organization administrator |
| `TNT_ADMIN` | SYSTEM | Platform-wide super-admin (wildcard `*` permission) |

Each enum entry carries its `defaultPermissions` set — the canonical permission set granted to that role when provisioned into a tenant.

---

### 2. `TntPermission` — Centralized Permission Catalog (Constants)

All TiiBnTick permission strings in `resource:action` format, organized by domain:

| Domain | Example Permissions |
|--------|---------------------|
| Mission | `mission:create`, `mission:assign`, `mission:start`, `mission:complete` |
| Delivery | `delivery:read`, `delivery:track`, `delivery:confirm`, `delivery:proof` |
| Announcement | `announcement:create`, `announcement:respond`, `announcement:elect` |
| Agency | `agency:read`, `agency:write`, `agency:manage` |
| Branch | `branch:read`, `branch:write`, `branch:manage` |
| Actor | `actor:read`, `actor:write`, `actor:approve`, `actor:suspend` |
| Billing | `billing:read`, `billing:write`, `billing:post` |
| Invoice | `invoice:read`, `invoice:issue` |
| Wallet | `wallet:read`, `wallet:write`, `payment:process` |
| Report | `report:read`, `report:export` |
| Trust | `trust:read`, `trust:verify`, `trust:anchor` |
| Relay | `relay:read`, `relay:write`, `relay:operate` |
| Dispute | `dispute:create`, `dispute:read`, `dispute:resolve` |
| Admin | `admin:roles`, `admin:users`, `admin:audit`, `admin:settings` |
| Media | `media:read`, `media:upload`, `media:delete` |
| Resource | `resource:read`, `resource:write`, `resource:reserve` |
| System | `system:admin`, `tenant:admin`, `org:admin` |

**Rule:** All TiiBnTick code must import from `TntPermission` — never hardcode permission strings.

---

### 3. `TntRoleDefinition` — Role Value Object for Provisioning

An immutable record built from `TntRole` entries used by `tnt-administration-core` when bootstrapping roles for a new tenant. No persistence responsibility — that belongs to the Kernel.

---

### 4. `TntPermissionContext` — Permission Evaluation Context

Carries `userId`, `tenantId`, `agencyId`, `actorId` for a permission check evaluation. Decouples the evaluator from Spring Security types.

---

### 5. `TntRoleDefinitionRegistry` — In-Memory Role Registry

A singleton Spring bean holding all `TntRoleDefinition` objects indexed by code. Provides:
- `getAllDefinitions()` — full list for tenant provisioning
- `findByCode(code)` / `getByCode(code)` — lookup
- `getSystemRoles()` / `getEditableRoles()` — filtered views
- `getRoleHierarchy()` — ordered list (highest privilege first)
- `hierarchyIndex(code)` — numeric rank for comparison

---

### 6. `TntPermissionEvaluator` — Reactive DSL (implements `CheckPermissionUseCase`)

The core reactive permission evaluation service. Delegates to the Kernel's `ReactivePermissionResolver` (which handles DB lookups + Redis caching at TTL=5min).

**Matching semantics** (checked in order):
1. `*` (global wildcard) — grants everything
2. `resource:action` (exact match)
3. `resource:*` (resource wildcard — any action)
4. `resource:action#SYSTEM` / `#TENANT` (broad scoped)
5. `resource:action#AGENCY:<agencyId>` (narrowly scoped)

**Fast path:** When a Spring Security reactive context is present (JWT already decoded by Kernel filter chain), permissions are read directly from `Authentication.getAuthorities()` — **no DB call**.

**API:**

```java
// Reactive DSL
Mono<Boolean>  can(ctx, "mission", "create")
Mono<Boolean>  cannot(ctx, "billing", "write")
Mono<Void>     assertCan(ctx, "report", "export")
Mono<Set<String>> resolvePermissions(ctx)
Mono<Boolean>  hasRole(ctx, "AGENCY_MANAGER")

// From HTTP reactive context (fast path — no DB)
Mono<Boolean>  canFromCurrentContext("delivery", "confirm")
Mono<Void>     assertCanFromCurrentContext("admin", "roles")
```

---

### 7. `TntRoleService` — implements `ResolveUserRolesUseCase`

Wraps the Kernel's `UserRoleAssignmentRepository` + `RoleRepository` to return strongly-typed `TntRole` values. Filters out unknown/non-TiiBnTick roles silently for forward compatibility.

**API:**

```java
Flux<TntRole>  resolveRoles(userId, tenantId)
Mono<Boolean>  hasRoleAssignment(userId, tenantId, "FREELANCER")
Mono<TntRole>  resolveHighestRole(userId, tenantId)
```

---

### 8. `@RequirePermission` + `TntPermissionAspect` — Declarative AOP Enforcement

```java
// On service methods
@RequirePermission(resource = "mission", action = "create")
public Mono<Mission> createMission(CreateMissionCommand cmd) { ... }

@RequirePermission(resource = "report", action = "export")
public Flux<ReportLine> exportReport(UUID tenantId) { ... }
```

- Supports `Mono<?>` and `Flux<?>` return types only (reactive-only)
- Uses fast-path (JWT authorities) — no DB call
- Emits `TntRoleException.forbidden()` if check fails
- Can be placed on method or class (class = default for all methods)
- Disabled via `tnt.roles.aop-enabled=false`

---

### 9. `TntRoleInitializationService` — Startup Provisioning

Triggered on `ApplicationReadyEvent`. Provisions all TiiBnTick role definitions into the Kernel DB for the system tenant using the Kernel's `CreateRoleUseCase`. **Idempotent** — skips roles that already exist.

Also provides `provisionForTenant(tenantId)` called by `tnt-administration-core` when a new tenant is onboarded.

---

### 10. `KernelRoleProvisioningAdapter` — Out Adapter

Implements `ITntRoleProvisioningPort` by calling:
- `CreateRoleUseCase.createRole(CreateRoleCommand)` — Kernel's role persistence
- `RoleRepository.existsByCode(tenantId, code)` — duplicate check
- `ReactivePermissionCache.invalidate(tenantId, userId)` — optional cache clear

---

## What This Module Does NOT Do

| NOT in tnt-roles-core | Where it lives |
|----------------------|----------------|
| `Role` entity CRUD | `RT-comops-roles-core` via `RoleRepository` |
| `UserRoleAssignment` persistence | `RT-comops-roles-core` via `UserRoleAssignmentRepository` |
| Permission resolution DB logic | `RT-comops-roles-core` → `RolesPermissionResolver` |
| Redis cache management | `RT-comops-kernel-core` → `ReactivePermissionCache` |
| Role management UI/API | `tnt-administration-core` |
| Per-tenant role provisioning at onboarding | `tnt-administration-core` calls `provisionForTenant()` |
| Authentication / JWT | `tnt-auth-core` + `RT-comops-auth-core` |

---

## Hexagonal Architecture Layout

```
tnt-roles-core/
├── domain/
│   ├── model/
│   │   ├── TntRole.java                 ← Enum: 9 business roles + permissions
│   │   ├── TntPermission.java           ← Constants: all permission strings
│   │   ├── TntRoleDefinition.java       ← Value object: role descriptor
│   │   └── TntPermissionContext.java    ← Value object: evaluation context
│   └── exception/
│       └── TntRoleException.java        ← Root domain exception
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── CheckPermissionUseCase.java       ← Primary port: DSL
│   │   │   └── ResolveUserRolesUseCase.java      ← Primary port: role lookup
│   │   └── out/
│   │       └── ITntRoleProvisioningPort.java      ← Secondary port: provisioning
│   └── service/
│       ├── TntPermissionEvaluator.java            ← Implements CheckPermissionUseCase
│       ├── TntRoleService.java                    ← Implements ResolveUserRolesUseCase
│       ├── TntRoleDefinitionRegistry.java         ← In-memory registry
│       └── TntRoleInitializationService.java      ← Startup provisioner
├── adapter/
│   ├── in/web/
│   │   ├── RequirePermission.java                 ← AOP annotation
│   │   └── TntPermissionAspect.java               ← Reactive AOP enforcement
│   └── out/kernel/
│       └── KernelRoleProvisioningAdapter.java     ← Calls Kernel CreateRoleUseCase
└── config/
    ├── TntRolesProperties.java
    └── TntRolesAutoConfiguration.java
```

---

## Dependencies

| Dependency | Reason |
|------------|--------|
| `RT-comops-kernel-core` | `ReactivePermissionResolver`, `ReactivePermissionCache` |
| `RT-comops-roles-core` | `CreateRoleUseCase`, `RoleRepository`, `UserRoleAssignmentRepository`, `RoleScopeType` |
| `tnt-common-core` | Shared TiiBnTick types |
| `spring-boot-starter-webflux` | Reactive types (`Mono`, `Flux`) |
| `spring-boot-starter-security` | `ReactiveSecurityContextHolder`, `GrantedAuthority` |
| `spring-boot-starter-aop` | AspectJ for `TntPermissionAspect` |
| `spring-boot-starter-data-redis-reactive` | Transitive for `ReactivePermissionCache` |

---

## Integration Flow (Permission Check — Fast Path)

```
HTTP Request (JWT already decoded by Kernel filter)
        ↓
[ApiKeyAuthenticationToken in ReactiveSecurityContextHolder]
  authorities = ["mission:create#AGENCY:<id>", "report:read#TENANT", ...]
        ↓
[@RequirePermission(resource="mission", action="create")]
[TntPermissionAspect.enforceMethodPermission()]
        ↓
[TntPermissionEvaluator.assertCanFromCurrentContext("mission", "create")]
  → reads authorities from ReactiveSecurityContextHolder (NO DB call)
  → applies TiiBnTick matching semantics (wildcard + scoped)
  → if denied: Mono.error(TntRoleException.forbidden("mission", "create"))
  → if allowed: proceeds to actual method
```

## Integration Flow (Permission Check — DB Path)

```
[Service layer — no HTTP context]
        ↓
[TntPermissionEvaluator.can(ctx, "billing", "write")]
        ↓
[Kernel RolesPermissionResolver.resolvePermissions(tenantId, userId)]
  → GET Redis: perms:{tenantId}:{userId}  (TTL 5min)
    MISS → SELECT FROM roles JOIN user_role_assignments (Kernel DB)
         → SET Redis: perms:{...} TTL 300s
  → returns Set<String> of permissions
        ↓
[TntPermissionEvaluator.matches(permissions, "billing", "write", agencyId)]
  → TiiBnTick wildcard + scope matching
  → returns boolean
```
