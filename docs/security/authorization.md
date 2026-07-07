# Purpose
How `@RequirePermission` actually enforces access control — the AOP mechanism, the matching semantics, and the pluggable resolver behind it.

# Summary
`@RequirePermission(resource, action)` is the single entry point for permission checks (service/use-case layer, not controller layer). `TntPermissionAspect` intercepts, `TntPermissionEvaluator` resolves+matches. Permission resolution is a two-tier fast-path/fallback design — see `security/permissions.md` for the full LOCAL/REMOTE/HYBRID resolver architecture behind the fallback.

# Details

## The annotation
```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String resource();
    String action();
    boolean required() default true;
}
```
`foundation/tnt-roles-core/.../adapter/in/web/RequirePermission.java`. Applies to methods returning `Mono<?>`/`Flux<?>` only — non-reactive methods throw `UnsupportedOperationException` if annotated (this codebase is reactive end-to-end, so this should never trigger in practice).

## `TntPermissionAspect` — the interceptor
`@Around` advice on `@RequirePermission`-annotated methods. Calls `TntPermissionEvaluator.assertCanFromCurrentContext(resource, action)`, which:
1. Reads `ApiKeyAuthenticationToken` authorities from `ReactiveSecurityContextHolder` (reactor `Context`, async-safe).
2. **Fast path**: if the JWT already carries `permissions` as authorities (`tryResolveFromCurrentAuthentication()`), match directly — zero DB/Kernel round-trip.
3. **Fallback**: if no authenticated context or empty permission set, delegates to the Kernel-SPI `ReactivePermissionResolver` (`kernelPermissionResolver.resolvePermissions(tenantId, userId)`) — this is the LOCAL/REMOTE/HYBRID-selectable implementation from `security/permissions.md`.
4. On denial: `Mono.error(TntRoleException.forbidden(resource, action))` injected into the reactive chain → surfaces as 403 (see `api/errors.md`).

## Matching semantics (`TntPermissionEvaluator.matches()`)
| Permission string | Matches |
|---|---|
| `*` | Everything (TNT_ADMIN only) |
| `mission:create` | Exact resource:action |
| `mission:*` | Any action on `mission` |
| `mission:create#AGENCY:<id>` | Exact match, scoped — only if the caller's `agencyId` equals `<id>`, OR the suffix is `#SYSTEM`/`#TENANT` (unscoped-equivalent) |

Scope suffix format: `resource:action#SCOPE:<id>`. This logic lives in one private method (`matches()`) — if you need a new scope type beyond AGENCY/SYSTEM/TENANT, that's the only place to touch.

## `hasRole()` — role-based (not permission-based) checks
`TntPermissionEvaluator.hasRole(ctx, roleCode)` checks for a `ROLE_<code>` authority — separate code path from `can()`/`assertCan()`, used sparingly (most checks should be permission-based, not role-based, per the resource:action model).

# Links
- `security/permissions.md` — the `ReactivePermissionResolver` LOCAL/REMOTE/HYBRID architecture
- `security/roles.md` — `TntRole` enum and default permission sets
- `api/security.md` — endpoint-level permission requirements
- `api/errors.md` — `TntRoleException` → HTTP 403

---
> **Comment maintenir ce document** : si la logique de `matches()` change (nouveau type de scope, nouvelle syntaxe de permission), mettre à jour le tableau "Matching semantics" en priorité — c'est la partie la plus susceptible de surprendre quelqu'un qui débogue un 403 inattendu.
