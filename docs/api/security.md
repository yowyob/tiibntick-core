# Purpose
How security manifests at the HTTP/endpoint level: which header/auth is required where, which permission strings gate which endpoints. For the underlying RBAC *mechanism* (JWT validation, resolver architecture), see `security/` — this doc is the endpoint-facing view.

# Summary
Every endpoint except explicitly-public ones (tracking by code, announcement browsing) requires a Bearer JWT. Fine-grained checks use `@RequirePermission(resource, action)` (service/use-case layer, AOP-enforced) — see `security/permissions.md` for the full permission catalog.

# Details

## Auth requirement by endpoint class
| Class | Requirement |
|---|---|
| Public tracking | None — `GET /track/{trackingCode}`, `GET /api/v1/tenants/{t}/delivery-announcements/open` |
| Standard authenticated | `Authorization: Bearer <JWT>` issued by Kernel (YowAuth0) |
| Webhook callbacks | Provider-specific signature/secret (MTN MoMo, Orange Money, Stripe) — NOT JWT |
| Permission-gated | JWT + `@RequirePermission(resource, action)` passes |
| Platform gateway | `X-Client-Id`/`X-Api-Key` (NOT a JWT) — `/api/v1/auth/**`, `/api/v1/sso/**`, `/api/v1/onboarding/**`, `/api/v1/platform/**`; scope-gated via `PlatformScopeAuthorizationManager`/`@RequirePlatformScope`, see `docs/auth/platform-client-management-design.md` |
| Platform admin | JWT + `@RequirePermission(resource="platform", action="clients")` — `/api/v1/admin/platform-clients/**`, `/api/v1/admin/api-keys/**`, `/api/v1/admin/scope-registry` (TNT_ADMIN only) |

## Representative `@RequirePermission` values by resource (non-exhaustive — see `security/permissions.md` for the canonical catalog)
| Resource | Actions seen | Typical roles |
|---|---|---|
| `mission`/`delivery` | `create`, `confirm`, `start`, `complete`, `assign`, `cancel` | `PERMANENT_DELIVERER`, `FREELANCER`, `AGENCY_MANAGER` |
| `relay` | `read`, `write`, `operate` | `RELAY_OPERATOR`, `AGENCY_MANAGER` |
| `agency`/`branch` | `read`, `write`, `manage` | `AGENCY_MANAGER`, `BRANCH_MANAGER`, `ORG_ADMIN` |
| `wallet`/`payment` | `read`, `write`, `process` | `CLIENT`, `FREELANCER` |
| `invoice`/`billing` | `read`, `write`, `issue`, `post` | `AGENCY_MANAGER` |
| `report` | `read`, `export` | `AGENCY_MANAGER`, `SUPPORT_AGENT` |
| `dispute` | `create`, `read`, `resolve` | `CLIENT`/`FREELANCER` (create/read own), `AGENCY_MANAGER`/`SUPPORT_AGENT`/`ORG_ADMIN` (resolve, any dispute in tenant) |
| `incident` | `create`, `read`, `manage` | `FREELANCER`/`PERMANENT_DELIVERER`/`RELAY_OPERATOR` (create/read own), `AGENCY_MANAGER`/`BRANCH_MANAGER`/`SUPPORT_AGENT`/`ORG_ADMIN` (manage, any incident in tenant) — added 2026-07-31, see `security/roles.md` |
| `administration:*` | `permissions:read`, `roles:read/write`, `settings:read/write` | `TNT_ADMIN`, `ORG_ADMIN` |
| `accounting`, `sales` | `read`, `write` | `ORG_ADMIN`, `tnt:platform:admin` fallback |
| `platform` | `clients` (platform-client CRUD/rotate/revoke/scopes/audit) | `TNT_ADMIN` only (not granted to any other role's defaults) |

## Required headers by module (multi-tenancy — see `api/rest.md` for the full pattern table)
`X-Tenant-Id` — sync, realtime SSE, billing-invoice, wallet, billing-report.
`X-Organization-Id` / `X-Agency-Id` — sales, accounting.
None (JWT-derived) — actor-core, administration, KYC, disputes, incidents — preferred pattern for new endpoints. Disputes/incidents still *accept* an `X-Tenant-Id`/`X-Actor-ID` header for backward compatibility, but it is inert: tenant and actor identity are always resolved from `@CurrentUser TntUserIdentity`, never a client-supplied header (dispute since Audit n°7 · #4, 2026-07-18; incident since the Go-Freelancer RBAC hardening, 2026-07-31 — see `knowledge/known-issues.md`).

## CORS
`tnt.security.allowed-origins` (default `http://localhost:3000,3001,4200` in dev) — see `tnt-bootstrap/src/main/resources/application.yml`.

# Links
- `security/authentication.md` — JWT validation mechanics
- `security/authorization.md` — `@RequirePermission`/`TntPermissionAspect` mechanics
- `security/permissions.md` — full `TntPermission` constant catalog
- `security/roles.md` — `TntRole` enum and default permissions per role
- `api/rest.md`, `api/errors.md`

---
> **Comment maintenir ce document** : ajouter une ligne au tableau "Representative @RequirePermission values" quand un nouveau `resource` apparaît dans le code (`grep -rn "@RequirePermission" --include=*.java`). Le catalogue exhaustif vit dans `security/permissions.md` — ce doc reste un résumé orienté endpoint.
