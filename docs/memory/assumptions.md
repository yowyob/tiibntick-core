# Purpose
Assumptions baked into the current implementation that haven't been explicitly confirmed by the Kernel team or product owner — revisit these if something unexpected happens at integration time.

# Summary
Mostly Kernel-contract assumptions (claim names, endpoint shapes, error semantics) and a couple of "this is probably temporary" infrastructure choices.

# Details

## Kernel API contract assumptions
| Assumption | Where used | Risk if wrong |
|---|---|---|
| JWT claims are named `tenant_id`, `actor_id`, `organization_id`, `agency_id`, `permissions` (snake_case) | `TntJwtValidator` | Auth context would silently build with null fields if the Kernel renames claims |
| `POST /v1/roles` will accept the exact `KernelCreateRoleRequest` shape (`tenantId`, `code`, `name`, `scopeTypeCode`, `defaultPermissions`) once implemented | `KernelRoleProvisioningAdapter` | Provisioning would fail with a 400 instead of 404 once the endpoint exists — should be re-tested then, not assumed correct |
| `GET /v1/permissions/resolve?userId=` will return a flat permission-string array | `RemoteReactivePermissionResolver` | Resolver parsing would need adjustment if the actual shape differs |
| 409 CONFLICT means "role already exists" (not some other conflict) | `KernelRoleProvisioningAdapter` | Idempotency check would be wrong if 409 means something else |

## Infrastructure assumptions
| Assumption | Where | Notes |
|---|---|---|
| Single `tnt-bootstrap` instance (no horizontal scaling yet) | `PermissionCache` design (Caffeine, not Redis) | Documented explicitly as acceptable *because* of this — re-evaluate if/when the app is scaled to multiple instances (cache invalidation via Kafka should still work correctly per-instance, but cache population would be duplicated work) |
| Dev MinIO/Postgres/Redis credentials in `docker-compose.yml` are dev-only and never reused in staging/prod | `application.yml` profile blocks | Staging/prod profiles already override with env-var-sourced secrets — verify this hasn't drifted if you're setting up a new environment |
| `tnt_system_tenant_id` (`00000000-0000-0000-0000-000000000001`) is stable across all environments | `tnt.roles.system-tenant-id` | CLAUDE.md states this explicitly ("MUST be identical across all environments") — flagged here as a hard assumption baked into role provisioning |

## Ownership/process assumptions
- Module ownership comments in root `pom.xml` (`architecture/modules.md`) are assumed current — verify with the team if attributing work, they're maintained inline but could drift.
- `RT-comops-*` artifacts are resolved from a private GitLab/GitHub package registry not visible in this repo — build instructions assume CI has the right tokens configured (`gitlab-ci` Maven profile).

# Links
- `architecture/decisions.md` — ADR-001 (Kernel boundary), ADR-004 (resolver architecture)
- `security/authentication.md`, `security/permissions.md`

---
> **Comment maintenir ce document** : ajouter une assumption dès qu'une décision est prise sans confirmation externe (API non encore testée contre la vraie implémentation, contrat supposé stable). Retirer une entrée dès qu'elle est confirmée correcte (et la noter dans `memory/completed.md` si la confirmation a nécessité du travail).
