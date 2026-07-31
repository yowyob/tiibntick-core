# Purpose
What's currently broken or degraded, right now — as opposed to `knowledge/known-issues.md`, which is the historical incident log (including resolved issues).

# Summary
No build- or boot-blocking problems as of 2026-07-25. Remaining gaps are the residual Phase 0 items tracked in `docs/audits/remediation/phase-0-critical.md` (real, documented, deliberately not silently closed) plus a couple of external (Kernel-side) gaps.

# Details

## Currently active (non-blocking)
| Problem | Symptom | Impact | Tracked in |
|---|---|---|---|
| No real multi-process concurrency test for RBAC | S5's Testcontainers-based tests simulate "two instances" as two connections in one JVM, not two real processes | RBAC's outbox-sync worker is unverified under true multi-instance contention | `docs/audits/remediation/phase-0-critical.md` (Chantier D DoD) |
| ArchUnit layering test not run in CI | `LayeringArchitectureTest` passes locally; no `.gitlab-ci.yml` or real build/test CI pipeline exists in this repo | A future layering violation wouldn't be caught automatically | `docs/audits/remediation/phase-0-critical.md` (Chantier E DoD) |
| 3 i18n keys missing from all 5 language packs | `notify.sub_deliverer.invited`, `notify.sub_deliverer.invitation_accepted`, `notify.billing.surcharge_triggered` used by production code | Those 3 specific notifications render with a fallback rather than the real message | `docs/audits/remediation/phase-0-critical.md` (Chantier F DoD) |
| Swagger/actuator reopened in prod | Deliberately reverted 2026-07-23 (commit `a8528051`) for an external compliance review | Attack-surface reconnaissance exposure, intentional and temporary | `docs/audits/remediation/phase-0-critical.md` (Chantier A · Audit 7 #10) — revert once the review concludes |
| `tnt-inventory-core` hub-package endpoints not tenant-scoped at any layer | `pickupHubPackage`, `getHubOccupancy`, `findOverduePackages` | Missing scoping entirely (not a spoofable-header IDOR, a different vulnerability class) | Flagged 2026-07-23, not yet fixed |
| No IDOR test exists under `coreBackend/` | — | Chantier A's DoD sub-criterion remains unmet | `docs/audits/remediation/phase-0-critical.md` |
| `tnt-product-core`/`tnt-accounting-core` Kernel catalog duplication | `Product` and `Account`/`JournalEntry` plausibly duplicate Kernel data the Kernel already manages more completely | Not a bug, a deferred architecture decision needing stakeholder input (financial-audit stakes for accounting) | `architecture/decisions.md` ADR-010/013 |
| Kernel `POST /api/auth/refresh` returns 401 for the system-tenant account | `AUTH_INVALID_REFRESH_TOKEN` immediately after a successful login | Treat as unusable; always redo the full discover-contexts/select-context flow | Kernel-side, not actionable from this repo |
| Host disk chronically near-full in dev environment | `~/.docker/desktop` dominates usage | Has caused several build-corruption false alarms (corrupted `~/.m2` jars, phantom `NoClassDefFoundError`) | Check `df -h /` before assuming a weird build error is a real code bug |

## Recently resolved (kept here briefly for "is this still broken?" lookups — full write-up in `knowledge/known-issues.md`)
| Problem | Resolved |
|---|---|
| R2DBC `Persistable` gap silently no-op'd all platform-client creation | 2026-07-25 |
| `RedisPresenceRepository.findAllStale()` `WRONGTYPE` on index keys | 2026-07-23 |
| Tenant IDOR via `X-Tenant-Id` header/body in product/inventory/resource-core | 2026-07-23 |
| Liquibase duplicate-classpath-resource crash on full-context tests | 2026-07-23 |
| Tracking codes never assigned at delivery creation | 2026-07-25 |
| GPS-to-Kalman ETA pipeline was a dead stub | 2026-07-25 |
| Every `Kernel*Adapter` skipped unwrapping the Kernel's response envelope | 2026-07-08 |
| `tnt_sync_session` table missing / MinIO wrong-bucket / `/swagger-ui.html` infinite spin / Lombok-JDK25 compile failure | 2026-06-30 |
| `@RequirePermission` missing bean in default profile | 2026-06-29 |

# Links
- `knowledge/known-issues.md` — full incident write-ups with root cause
- `memory/current-state.md` — overall health snapshot
- `memory/todo.md` — actionable next steps
- `docs/audits/remediation/phase-0-critical.md` — authoritative Phase 0 status, checkbox-tracked

---
> **Comment maintenir ce document** : ajouter une ligne à "Currently active" dès qu'un problème réel est découvert et non résolu en fin de session. Déplacer vers "Recently resolved" dès la résolution, puis retirer complètement après ~1-2 sessions (l'historique complet reste dans `knowledge/known-issues.md`).
