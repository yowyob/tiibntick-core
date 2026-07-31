# Purpose
The 10 canonical `TntRole` values — what each one is, its scope, and its default permission set. Source: `foundation/tnt-roles-core/.../domain/model/TntRole.java` (verified directly, not inferred).

# Summary
10 system roles, each with a fixed scope type (`SYSTEM`/`ORGANIZATION`/`AGENCY`/`TENANT`) and an immutable default permission set defined inline in the enum. `TNT_ADMIN` is the only role with the global wildcard `*`.

# Details

## Role table

| Role | Scope | Permissions | Summary |
|---|---|---|---|
| `AGENCY_MANAGER` | AGENCY | 52 | Full agency ops: missions, staff, billing, reports, resources, settings, admin (roles/users/audit), incident/dispute management |
| `BRANCH_MANAGER` | AGENCY | 26 | Daily branch (antenne) operations — mission lifecycle, local staff, read-only billing/reports, incident/dispute management |
| `PERMANENT_DELIVERER` | AGENCY | 18 | Salaried deliverer — start/complete missions, confirm delivery, upload proof, report/read incidents |
| `FREELANCER` | TENANT | 23 | Independent deliverer — same delivery actions as permanent, plus wallet write + payment processing, report/read incidents, create/read disputes |
| `RELAY_OPERATOR` | AGENCY | 17 | Hub/relay point — confirm/track deliveries through the hub, trust anchoring, report/read incidents |
| `AGENCY_HUB_OPERATOR` | AGENCY | 18 | Manages an agency-owned relay hub — deposit/withdraw validation, stock, QR handoffs, report/read incidents |
| `CLIENT` | TENANT | 18 | End sender — create/manage announcements, track, wallet, payment, raise disputes, read incidents on own shipments |
| `SUPPORT_AGENT` | TENANT | 16 | Read-only across most modules + dispute resolution + incident management + audit |
| `ORG_ADMIN` | ORGANIZATION | 36 | Multi-agency administrator — agency/branch/actor management, billing, admin governance, incident/dispute management |
| `TNT_ADMIN` | SYSTEM | 1 (`*`) | Platform super-admin, wildcard — cannot be assigned to normal users |

(Permission counts recounted directly from `TntRole.java` as of the `incident:*` RBAC hardening — Go-Freelancer integration, 2026-07-31. Re-count if `TntRole.java` changes again.)

## Scope types (`RoleScopeType`, Kernel-defined enum)
- `SYSTEM` — platform-wide (TNT_ADMIN only)
- `ORGANIZATION` — multi-agency conglomerate (ORG_ADMIN)
- `AGENCY` — single agency (AGENCY_MANAGER, BRANCH_MANAGER, PERMANENT_DELIVERER, RELAY_OPERATOR, AGENCY_HUB_OPERATOR)
- `TENANT` — cross-agency, scoped to the tenant (FREELANCER, CLIENT, SUPPORT_AGENT)

## `TntRoleDefinitionRegistry`
In-memory singleton, built once at construction from the `TntRole` enum (`TntRoleDefinition.from(TntRole)` for each of the 10 values). Not persisted to DB — that's the Kernel's job once `KernelRoleProvisioningAdapter`'s `POST /v1/roles` calls succeed (currently 404, see `security/permissions.md`). All 10 are `systemRole=true`/non-editable.

## Incident/dispute permissions — Go-Freelancer integration (2026-07-31)
`tnt-incident-core` and `tnt-dispute-core` previously had no (incident) or only "is authenticated" (dispute) access control at all — any authenticated JWT of any role could reach mediator/manager-tier actions on any tenant's incidents/disputes. Both modules now depend on `tnt-roles-core` and gate their application-service methods with `@RequirePermission`:
- `incident:create` / `incident:read` / `incident:manage` (new — see `security/permissions.md`), plus a manual actor-ownership check (`IncidentRequesterContext`) and tenant check in `IncidentQueryService`/`IncidentController`: non-privileged callers (no `incident:manage`) only see/act on incidents they reported themselves.
- `dispute:create` / `dispute:read` / `dispute:resolve` (pre-existing) now actually enforced on `DisputeCommandService`/`DisputeQueryService`/`EvidenceApplicationService` (previously the controllers only had `@PreAuthorize("isAuthenticated()")`, and the `X-Actor-ID` header trusted for mediator identity was client-forgeable — both fixed). Non-privileged callers (no `dispute:resolve`) only see/act on disputes where they are the claimant or the respondent.

`FREELANCER`/`PERMANENT_DELIVERER`/`RELAY_OPERATOR`/`AGENCY_HUB_OPERATOR` got `incident:create`/`incident:read` added; `FREELANCER` also got `dispute:read` (it already had `dispute:create`); `CLIENT` got `incident:read`; the manager/admin tier roles (`AGENCY_MANAGER`, `BRANCH_MANAGER`, `SUPPORT_AGENT`, `ORG_ADMIN`) got `incident:read`/`incident:manage`.

## Where roles are consumed
- `LocalReactivePermissionResolver` — unions a user's assigned role's default permissions with the registry.
- `TntRoleInitializationService` — provisions all 10 into the Kernel DB at startup (`ApplicationReadyEvent` hook), idempotent.
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
