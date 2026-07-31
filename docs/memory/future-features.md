# Purpose
Capability gaps visible in the code (scaffolds, unused-but-present dependencies, designed-but-unimplemented integrations) — the "what's this for if nothing uses it yet" list.

# Summary
Updated 2026-07-31. Several items from the previous snapshot (2026-06-30) are now built and activated — REMOTE/HYBRID Kernel permission resolution is still dormant, but role provisioning, blockchain-anchoring wiring, and the HRM/KYC gateway all went from "scaffold" to "shipped." New scaffolds/deferred items have appeared since: trust's resilience layer, curated platform-gateway business-module proxies, and DID issuance's real key custody.

# Details

## Built, not yet activated
| Feature | Where | Activates when |
|---|---|---|
| `RemoteReactivePermissionResolver` / `HybridReactivePermissionResolver` | `tnt-roles-core` | Kernel ships `GET /v1/permissions/resolve` + `tnt.roles.permission.mode` switched from `LOCAL` |
| Elasticsearch (dependency present) | (none — transitive only) | A module defines a `@Document` model + search use-case (incident/dispute/mission full-text search are the most likely candidates) |
| WORM/Object Lock retention on `tnt-incident-evidences` bucket | `tnt-media-core`/MinIO | Production deployment — currently created without lock, "configure via MinIO admin for production" per code comment |
| Curated platform-gateway business-module proxy controllers (e.g. a `PlatformDeliveryProxyController`) | `tnt-platform-gateway-core` scope model supports it, none built | Real platform demand — the design's own rule is "build the generic mechanism now, proxy endpoints only on real demand"; today only `AUTH`/`SSO`/`ONBOARDING` (+ curated `DISPUTE`/`SALES` internal proxies added 2026-07-18 for agency server-to-server calls) are real |
| Trust resilience layer (circuit breakers, retry queue, connectivity poller) | Would live in `tnt-trust-core` | `resilience4j` isn't on the classpath anywhere in this repo yet — explicitly deferred from the original trust design doc §15 (`TrustAvailabilityGuard`, dual circuit breakers, `trust_retry_queue` with `SKIP LOCKED`); every anchor point today uses simple `.onErrorResume()` containment instead |
| Real cryptographic org-controlled DID signing | `tnt-trust-core`'s DID adapters | A proper key-custody design is done — today the trust-side adapter generates and discards a throwaway keypair per issuance, so neither actor nor FreelancerOrg genuinely holds a signing key |

## Now shipped (moved out of "scaffold" since the 2026-06-30 snapshot)
- **Kernel role-provisioning** (`POST /api/roles`) — the Kernel does have this endpoint (the earlier "404, not implemented yet" note was stale); `KernelRoleProvisioningAdapter` and the outbox-based RBAC sync worker (`KernelRoleSyncWorker`, `KernelRoleReconciliationJob`) use it for real. See `docs/audits/remediation/rbac-s5-outbox-architecture.md`.
- **`PermissionCacheInvalidationListener`** (Kafka topic `tnt.roles.permission-changed`) — now has a real producer (`IPermissionChangeNotifier`, wired into `TntRoleAssignmentService`/`TntRoleRevocationService`, 2026-07-23).
- **Blockchain anchoring** (`tnt-trust-core`) — went from a partially-orphaned subtree import to fully wired across 8 calling modules (delivery, incident, billing-pricing, billing-wallet, dispute, actor badges+DID, realtime geofences, organization FreelancerOrg DID). See `architecture/decisions.md` ADR-018.
- **HRM + KYC Kernel gateway** — `tnt-hrm-core` (160 ops across 16 controllers) and the actor-core KYC verification proxy, both built 2026-07-11 (ADR-019).
- **Platform Client-ID/API-Key system** — went from a static `.env` config to a full DB-backed admin-managed system with rotation/revocation, live in production (ADR-017).
- **Tracking codes + GPS-to-Kalman ETA pipeline** — both were "wired but never connected" as of the 2026-06-30 snapshot's timeframe; now real end-to-end (2026-07-25, see `knowledge/known-issues.md` #21/#22).

## Not built, inferable from module purpose but no scaffold exists
- Full audit trail UI/API beyond `ADMIN_AUDIT` permission gating (the permission exists, the audit log retrieval surface isn't fully fleshed out).
- Multi-currency consolidated reporting in `tnt-billing-report` (per-country VAT exists in `tnt-billing-invoice`, but cross-country roll-up reporting isn't evident).
- Tenant-scoping for `tnt-inventory-core`'s hub-package lookup endpoints (`pickupHubPackage`, `getHubOccupancy`, `findOverduePackages`) — missing at any layer, not yet designed.
- Real-time push for the Link network map (`tnt-link-back-core`) — today 100% REST polling, no PostGIS; the target design (BFF Link + geohash fan-out reusing `tnt-realtime-core`) is documented in `docs/audits/remediation/phase-1-hardening.html` but not started.

# Links
- `development/roadmap.md` — the engineering-debt framing of the same gaps
- `security/permissions.md` — REMOTE/HYBRID detail
- `security/platform-gateway-credentials.md`, `architecture/decisions.md` ADR-017/018/019
- `infrastructure/elasticsearch.md`, `infrastructure/kafka.md`

---
> **Comment maintenir ce document** : déplacer un item vers `memory/completed.md` dès qu'il est implémenté. Ajouter un item dès qu'un nouveau scaffold/no-op/fallback est créé intentionnellement pour un usage futur.
