# Purpose
Concrete next-session priorities — things explicitly deferred or flagged as "ask before doing."

# Summary
No urgent open items as of 2026-07-25 — the last major push (Phase 0 remediation + platform-client rollout + tracking/ETA pipeline) closed out everything that was flagged mid-work. Remaining items are either genuinely deferred architecture decisions or opportunistic tech debt.

# Details

## Pending decisions (ask the user before acting)
- **`tnt-product-core` / `tnt-accounting-core` Kernel-catalog duplication** (ADR-010/013) — real, substantial duplication found, deliberately not executed. Needs a dedicated session: backfill strategy for existing local-only rows, read-consistency decision (proxy-through vs. sync/cache), and for accounting specifically, stakeholder sign-off given financial-audit/compliance stakes. Don't start this without re-confirming scope first.
- **Platform-client scope narrowing** — all 5 prod platform clients (Agency/Link/Go/Market/App Mobile) currently run on the default `*` wildcard scope. Narrowing any of them to least-privilege is a product decision (which platform needs which `resource:action` scopes), not something to guess at.

## Opportunistic follow-ups (no urgency, pick up if touching related code)
| Item | Trigger to act |
|---|---|
| Remove `tnt-organization-core` from `tnt-go-freelancer-point-back-core`'s pom.xml | Already flagged in-code as a `// TODO`; confirmed 2026-07-31 via grep that 0 files import it |
| Expand `tnt-go-freelancer-point-back-core` test coverage beyond the escrow/adapter/outbox tests added 2026-07-31 | The other 27 controllers and most application services still have zero tests — the 2026-07-31 pass targeted the highest-risk logic (money movement, the new port adapter), not full coverage |
| Add IDOR tests under `coreBackend/` | Chantier A's DoD is otherwise met — this is the one remaining unmet sub-criterion |
| Wire the ArchUnit `LayeringArchitectureTest` into a real CI pipeline | If/when a CI pipeline (GitLab CI, GitHub Actions build stage) is set up for this repo at all — none exists today |
| Add the 3 missing i18n keys (`notify.sub_deliverer.invited`, `notify.sub_deliverer.invitation_accepted`, `notify.billing.surcharge_triggered`) to all 5 language packs | Next time touching notification templates |
| Tenant-scope `tnt-inventory-core`'s hub-package endpoints (`pickupHubPackage`, `getHubOccupancy`, `findOverduePackages`) | Next time touching `tnt-inventory-core` |
| Revert Swagger/actuator prod exposure (`a8528051`) once the external compliance review concludes | When the review is confirmed done — don't let this linger |
| Consolidate `Money` value object (duplicated in tnt-billing-cost/invoice/wallet) into `tnt-common-core` | Next time a currency/rounding bug is fixed |
| Re-enable JaCoCo coverage gate (`check` goal, currently commented out in root `pom.xml`) | If asked to improve test rigor |
| Cross-check remaining `KernelXxxDto` field names against real Kernel schemas (`KernelActorDto`, `KernelOrganizationDto`, `KernelThirdPartyDto`, `KernelProductDto`, `KernelPermissionDto`, `KernelRoleDto`) | Before relying on any of their `existsAndActive` checks for anything beyond "did the Kernel respond" |
| Real cryptographic key-custody design for actor/FreelancerOrg DID signing | If real org-controlled signing is ever needed — today the trust-side adapter generates and discards a throwaway keypair per issuance, so the org never actually holds its own private key |

## Waiting on external (Kernel team) — not actionable from this repo
- `POST /api/auth/refresh` returns `401 AUTH_INVALID_REFRESH_TOKEN` for the system-tenant account even right after a successful login — report if this is meant to work.
- No documented single-actor-by-id `GET` endpoint on `actor-controller` — several fail-open existence checks (`KernelActorAdapter`) depend on this eventually existing.

## Documentation maintenance
- `docs/memory/*.md` had drifted ~3-4 weeks stale (last touched 2026-06-30/07-08) relative to actual work — refreshed 2026-07-31 by reconciling against the Claude memory system. Going forward, update this tree at the end of each significant session as originally intended, rather than letting the Claude memory system be the only place session-to-session continuity lives.

# Links
- `development/roadmap.md` — the engineering-debt version of this list (more detail, less "do this next")
- `memory/known-problems.md`, `memory/future-features.md`
- `docs/audits/remediation/phase-0-critical.md` — Phase 0's own residual-item list (overlaps with some entries above)

---
> **Comment maintenir ce document** : retirer un item dès qu'il est fait (et l'ajouter à `memory/completed.md`). Ajouter un item dès qu'une tâche est explicitement reportée par l'utilisateur ("fais ça plus tard", "pas maintenant").
