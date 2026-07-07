# Purpose
Capability gaps visible in the code (scaffolds, unused-but-present dependencies, designed-but-unimplemented integrations) — the "what's this for if nothing uses it yet" list.

# Summary
Three forward-looking scaffolds exist today with no consumer/producer: Kafka permission-change invalidation, Elasticsearch search, and REMOTE/HYBRID Kernel permission resolution. All were built deliberately for future use, not abandoned mid-work.

# Details

## Built, not yet activated
| Feature | Where | Activates when |
|---|---|---|
| `RemoteReactivePermissionResolver` / `HybridReactivePermissionResolver` | `tnt-roles-core` | Kernel ships `GET /v1/permissions/resolve` + `tnt.roles.permission.mode` switched from `LOCAL` |
| `PermissionCacheInvalidationListener` (Kafka topic `tnt.roles.permission-changed`) | `tnt-roles-core` | Any module (likely `tnt-administration-core`) starts publishing role/permission-mutation events |
| `KernelRoleProvisioningAdapter` | `tnt-roles-core` | Kernel ships `POST /v1/roles` (currently 404) |
| Elasticsearch (dependency present) | (none — transitive only) | A module defines a `@Document` model + search use-case (incident/dispute/mission full-text search are the most likely candidates given module purposes) |
| WORM/Object Lock retention on `tnt-incident-evidences` bucket | `tnt-media-core`/MinIO | Production deployment — currently created without lock, "configure via MinIO admin for production" per code comment |

## Not built, inferable from module purpose but no scaffold exists
- Full audit trail UI/API beyond `ADMIN_AUDIT` permission gating (the permission exists, the audit log retrieval surface isn't fully fleshed out).
- Multi-currency consolidated reporting in `tnt-billing-report` (per-country VAT exists in `tnt-billing-invoice`, but cross-country roll-up reporting isn't evident).

# Links
- `development/roadmap.md` — the engineering-debt framing of the same gaps
- `security/permissions.md` — REMOTE/HYBRID detail
- `infrastructure/elasticsearch.md`, `infrastructure/kafka.md`

---
> **Comment maintenir ce document** : déplacer un item vers `memory/completed.md` dès qu'il est implémenté. Ajouter un item dès qu'un nouveau scaffold/no-op/fallback est créé intentionnellement pour un usage futur.
