# Purpose
Snapshot of "where things stand right now" — what's verified working, what's known-broken, as of the last update. This is the **first file to check** at the start of a new session.

# Summary
**As of 2026-07-25**: the full reactor builds and boots cleanly, Kernel is HTTP-only end-to-end (zero `yowyob.comops.api:RT-comops-*` Maven dependencies anywhere in the repo), Phase 0 of the audit remediation plan is substantially closed (see `docs/audits/remediation/phase-0-critical.md` for the authoritative, checkbox-tracked status — Chantiers A/B/C/G done, D/E/F have real, documented residual gaps), and a DB-backed platform Client-ID/API-Key system is live in production with 5 platform identities issued. No known build-blocking issues remain.

# Details

## Build & runtime status
| Check | Status |
|---|---|
| `mvn clean install` (full reactor, ~55 modules) | ✅ passes |
| `mvn -pl tnt-bootstrap spring-boot:run` | ✅ starts cleanly |
| Kernel dependency boundary | ✅ HTTP/Kafka only — zero `RT-comops-*-core` Maven deps or Kernel class imports anywhere (verified 2026-07-08) |
| `docs/kernel-api/` (offline Kernel API mirror) | ✅ current as of 2026-07-08, refresh via `scripts/fetch-kernel-openapi.sh` if the Kernel changes |
| Platform gateway (`tnt-platform-gateway-core`) | ✅ live in prod (`tiibntick-core.yowyob.com`), 5 platform clients issued — see `security/platform-gateway-credentials.md` |
| `tnt-trust-core` blockchain anchoring | ✅ wired to 8 modules (delivery, incident, billing-pricing, billing-wallet, dispute, actor, realtime, organization) — see `architecture/decisions.md` ADR-018 |
| Tracking codes + GPS→ETA pipeline | ✅ real end-to-end as of 2026-07-25 (previously silently dead — see `knowledge/known-issues.md` #21/#22) |
| RBAC | ✅ own R2DBC-backed schema (`tnt-roles-core`) is now the real default (not in-memory), outbox-synced to the Kernel — see `docs/audits/remediation/rbac-s5-outbox-architecture.md` |

## Known non-blocking gaps (see `docs/audits/remediation/phase-0-critical.md` "Definition of Done" for the authoritative, itemized list)
- Chantier D (multi-instance locks): dispute/wallet have real Testcontainers proof, but simulate "two instances" as two connections in one JVM, not two real processes; RBAC has zero concurrency test.
- Chantier E (ArchUnit layering test): passes locally, but no CI pipeline in this repo actually runs it (no `.gitlab-ci.yml`, GitHub Actions is a webhook-only dispatcher).
- Chantier F (i18n): 3 keys used by production code (`notify.sub_deliverer.invited`, `notify.sub_deliverer.invitation_accepted`, `notify.billing.surcharge_triggered`) still missing from all 5 language packs.
- Swagger/actuator exposure in prod was deliberately reopened 2026-07-23 for an external compliance review (commit `a8528051`) — watch that this doesn't stay open past the review.
- `tnt-product-core`/`tnt-accounting-core` Kernel-catalog-duplication (ADR-010/013) — flagged, deliberately not executed (financial-audit/data-migration stakes need dedicated stakeholder input first).
- Kernel `POST /api/auth/refresh` appears broken for the system-tenant account (`401 AUTH_INVALID_REFRESH_TOKEN` even right after login) — always redo the full discover-contexts/select-context login instead of relying on refresh.
- Host disk chronically near-full in this dev environment (dominated by `~/.docker/desktop`) — has caused several build-corruption false alarms (`NoClassDefFoundError` on classes that demonstrably exist, corrupted `~/.m2` jars); `df -h /` before assuming a weird single-occurrence build error is a real code bug.

## What changed most recently (reverse chronological — see `memory/completed.md` for full history)
1. **2026-07-25**: tracking codes wired end-to-end; GPS-to-Kalman ETA pipeline made real (previously a dead stub); R2DBC `Persistable` bug fixed (had silently blocked all platform-client creation since 2026-07-09); 5 platform-client identities issued in prod.
2. **2026-07-23**: Braun's real Kernel account/tenant (`af1f5fb6-...`) wired as the local system tenant; 4 parallel-agent branches fixed tenant-IDOR gaps (product/inventory/resource-core) and a Redis presence key-collision bug; Phase 0 Chantier C P5 (27 Kafka publishers → transactional outbox) fully closed; DoD verdicts recorded for all 6 Phase 0 chantiers.
3. **2026-07-11**: `tnt-hrm-core` (new L4 module, 160 Kernel HRM operations proxied) + KYC document-verification proxy in `tnt-actor-core`.
4. **2026-07-10**: all 10 modules wired to `tnt-trust-core` for blockchain anchoring; `tnt-trust-core` promoted to its own L6 layer; strict cross-layer dependency rule formalized in `CLAUDE.md`.
5. **2026-07-09**: `tnt-platform-gateway-core` (new foundation module) built end-to-end — DB-backed Client-ID/API-Key replacing the `.env`-based mechanism, two-level `resource:action` scope model.
6. **2026-07-08**: Kernel HTTP-only migration completed (zero Maven/class dependency on the Kernel repo); "Core as Kernel facade/BFF" review across all 10 mandated modules (ADR-008–016), headline finding being that every `Kernel*Adapter` except `tnt-auth-core`'s skipped unwrapping the Kernel's response envelope.
7. **(earlier, pre-2026-07-08)**: see `memory/completed.md`'s 2026-06-30 and earlier entries — Spring Boot 4 compatibility chain, `@RequirePermission` hybrid resolver, Liquibase build-stabilization cleanup.

## Environment notes (don't re-derive these)
- Dev infra: `docker-compose.yml` in `tnt-bootstrap/`, see `infrastructure/docker.md`.
- JDK in use: 25.0.1 — Lombok version pinned partly because of this.
- Local dev Postgres now binds `5433:5432` (system Postgres on 5432 conflicted); `application.yml` defaults reflect this.
- Test credentials, Kernel login flow, and the platform-client production roster live in the Claude memory system (`~/.claude/projects/.../memory/kernel-auth-flow.md`, `platform-clients-prod-2026-07-24.md`) rather than this repo — those contain live secrets/tenant IDs that shouldn't be committed to git. This file and `security/platform-gateway-credentials.md` cover the mechanism/architecture; the actual current secret values are intentionally not duplicated into version control.

# Links
- `memory/completed.md` — full work log
- `memory/known-problems.md` — what's still open
- `knowledge/known-issues.md` — incident write-ups
- `docs/audits/remediation/phase-0-critical.md` — authoritative, checkbox-tracked remediation status

---
> **Comment maintenir ce document** : mettre à jour la section "Build & runtime status" et "What changed most recently" à la fin de CHAQUE session de travail significative. C'est le document qui évite de re-vérifier des choses déjà vérifiées.
