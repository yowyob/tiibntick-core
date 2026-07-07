# Purpose
The 9 canonical `TntRole` values — what each one is, its scope, and its default permission set. Source: `foundation/tnt-roles-core/.../domain/model/TntRole.java` (verified directly, not inferred).

# Summary
9 system roles, each with a fixed scope type (`SYSTEM`/`ORGANIZATION`/`AGENCY`/`TENANT`) and an immutable default permission set defined inline in the enum. `TNT_ADMIN` is the only role with the global wildcard `*`.

# Details

## Role table

| Role | Scope | ~Permissions | Summary |
|---|---|---|---|
| `AGENCY_MANAGER` | AGENCY | 33 | Full agency ops: missions, staff, billing, reports, resources, settings, admin (roles/users/audit) |
| `BRANCH_MANAGER` | AGENCY | 16 | Daily branch (antenne) operations — mission lifecycle, local staff, read-only billing/reports |
| `PERMANENT_DELIVERER` | AGENCY | 13 | Salaried deliverer — start/complete missions, confirm delivery, upload proof |
| `FREELANCER` | TENANT | 13 | Independent deliverer — same delivery actions as permanent, plus wallet write + payment processing |
| `RELAY_OPERATOR` | AGENCY | 9 | Hub/relay point — confirm/track deliveries through the hub, trust anchoring |
| `CLIENT` | TENANT | 11 | End sender — create/manage announcements, track, wallet, payment, raise disputes |
| `SUPPORT_AGENT` | TENANT | 10 | Read-only across most modules + dispute resolution + audit |
| `ORG_ADMIN` | ORGANIZATION | 27 | Multi-agency administrator — agency/branch/actor management, billing, admin governance |
| `TNT_ADMIN` | SYSTEM | 1 (`*`) | Platform super-admin, wildcard — cannot be assigned to normal users |

(Permission counts are approximate snapshots — `TntRole.java` is the source of truth, re-count if precision matters.)

## Scope types (`RoleScopeType`, Kernel-defined enum)
- `SYSTEM` — platform-wide (TNT_ADMIN only)
- `ORGANIZATION` — multi-agency conglomerate (ORG_ADMIN)
- `AGENCY` — single agency (AGENCY_MANAGER, BRANCH_MANAGER, PERMANENT_DELIVERER, RELAY_OPERATOR)
- `TENANT` — cross-agency, scoped to the tenant (FREELANCER, CLIENT, SUPPORT_AGENT)

## `TntRoleDefinitionRegistry`
In-memory singleton, built once at construction from the `TntRole` enum (`TntRoleDefinition.from(TntRole)` for each of the 9 values). Not persisted to DB — that's the Kernel's job once `KernelRoleProvisioningAdapter`'s `POST /v1/roles` calls succeed (currently 404, see `security/permissions.md`). All 9 are `systemRole=true`/non-editable.

## Where roles are consumed
- `LocalReactivePermissionResolver` — unions a user's assigned role's default permissions with the registry.
- `TntRoleInitializationService` — provisions all 9 into the Kernel DB at startup (`ApplicationReadyEvent` hook), idempotent.
- `api/security.md` — which roles typically gate which endpoints (representative, not exhaustive).
- `TntOpenApiConfig.buildBearerSchemeDescription()` (`tnt-bootstrap`) — documents this exact table in the Swagger UI bearer-auth description.

## Adding a new role (rare — these are meant to be stable/canonical)
1. Add an enum constant to `TntRole.java` with code/label/scope/permissions.
2. `TntRoleDefinitionRegistry` picks it up automatically (built from the enum).
3. Update this doc's table.
4. Update `TntOpenApiConfig`'s Swagger description so the API docs stay in sync.

# Links
- `security/permissions.md` — how permissions are resolved/cached
- `security/authorization.md` — matching semantics
- `api/security.md` — endpoint-level role usage

---
> **Comment maintenir ce document** : si `TntRole.java` change (nouveau rôle, permission ajoutée/retirée), mettre à jour le tableau immédiatement — relire le fichier directement plutôt que de deviner, les comptes de permissions sont sensibles aux erreurs de copier-coller.
