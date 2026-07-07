# Purpose
Forward-looking items that are visible *in the code* (scaffolds, TODOs, explicitly-deferred work) — not a product roadmap, just the engineering debt/follow-up list derivable from the codebase itself.

# Summary
Biggest visible gap: the Kernel doesn't yet expose the permission-resolution (`GET /v1/permissions/resolve`) or role-provisioning (`POST /v1/roles`) REST endpoints that `tnt-roles-core` is already built to call. Several other modules have no-op/scaffold adapters waiting for a real implementation.

# Details

## Kernel-side gaps (blocking, not fixable from this repo)
| Gap | Affected | Current behavior |
|---|---|---|
| `POST /v1/roles` doesn't exist | `KernelRoleProvisioningAdapter` | 404 on every startup role-provisioning attempt — logged, non-fatal |
| `GET /v1/permissions/resolve` doesn't exist | `RemoteReactivePermissionResolver` | Degrades to empty permission set — only matters if `tnt.roles.permission.mode` is `REMOTE`/`HYBRID` (default is `LOCAL`, unaffected) |

## Scaffolds with no producer/consumer yet
| Scaffold | Module | Waiting on |
|---|---|---|
| `PermissionCacheInvalidationListener` (Kafka topic `tnt.roles.permission-changed`) | tnt-roles-core | Some module's role/permission-mutation flow (likely `tnt-administration-core`) to actually publish to this topic |
| Elasticsearch (dependency present, unused) | (transitive, no owning module) | A concrete search use-case (incident/dispute/mission full-text search) |

## Known technical debt (not blocking, worth fixing opportunistically)
| Item | Where | Why it matters |
|---|---|---|
| `Money` defined independently in 3+ billing modules | tnt-billing-cost, tnt-billing-invoice, tnt-billing-wallet | Currency/rounding bugs must be fixed in every copy; should consolidate into `tnt-common-core` |
| Response wrapping inconsistency (`ApiResponse<T>` vs. plain DTO) | tnt-actor-core vs. billing/incident/dispute modules | API consumers can't rely on one shape across the platform |
| JaCoCo coverage gate commented out | root `pom.xml` | No enforced minimum test coverage |
| `tnt-incident-core` domain events not individually documented (marker class only) | tnt-incident-core | `domain/events.md` has a gap here — worth a follow-up pass directly in the module |

## Recently closed (for context — see `memory/completed.md` for the full session log)
- `@RequirePermission` hybrid LOCAL/REMOTE/HYBRID resolver — **done**, this is the architecture described in `security/permissions.md`.
- Liquibase `includeAll` → explicit `include:` cleanup across ~20 modules — **done**.
- springdoc/Lombok/swagger-jakarta version conflicts (Spring Boot 4 compatibility) — **done**.
- `tnt_sync_session` table wiring, MinIO incident-evidence bucket bug — **done**.

# Links
- `security/permissions.md` — the REMOTE/HYBRID modes waiting on the Kernel
- `domain/value-objects.md` — `Money` duplication note
- `memory/completed.md`, `memory/todo.md`, `memory/future-features.md`

---
> **Comment maintenir ce document** : déplacer un item de "Known technical debt"/"Kernel-side gaps" vers "Recently closed" dès qu'il est résolu, avec une ligne dans `memory/completed.md`. Ajouter un nouvel item dès qu'un scaffold/TODO visible dans le code est découvert.
