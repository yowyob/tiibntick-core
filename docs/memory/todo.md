# Purpose
Concrete next-session priorities — things explicitly deferred or flagged as "ask before doing."

# Summary
No urgent open items as of 2026-06-30 — the last two flagged issues (sync table, MinIO) were resolved same-day. Remaining items are opportunistic tech debt, not blockers.

# Details

## Pending decisions (ask the user before acting)
- None currently open.

## Opportunistic follow-ups (no urgency, pick up if touching related code)
| Item | Trigger to act |
|---|---|
| Consolidate `Money` value object (duplicated in tnt-billing-cost/invoice/wallet) into `tnt-common-core` | Next time a currency/rounding bug is fixed — fix all copies and consolidate while you're in there |
| Standardize API response wrapping (`ApiResponse<T>` vs plain DTO) | Next time a new controller is added in a module that hasn't picked a convention yet |
| Document `tnt-incident-core`'s individual domain events (currently a marker class, not itemized in `domain/events.md`) | If working in `tnt-incident-core` anyway |
| Re-enable JaCoCo coverage gate (`check` goal, currently commented out in root `pom.xml`) | If asked to improve test rigor |

## Waiting on external (Kernel team) — not actionable from this repo
- `POST /v1/roles` (role provisioning)
- `GET /v1/permissions/resolve` (permission resolution — only matters if switching off `LOCAL` mode)

## Documentation maintenance
- This `docs/` tree was just created (2026-06-30) — no maintenance cycle yet. First real test of "does this docs tree actually save tokens" will be the next session that touches this codebase; if a doc is found stale/wrong during that session, fix it immediately rather than letting it compound.

# Links
- `development/roadmap.md` — the engineering-debt version of this list (more detail, less "do this next")
- `memory/known-problems.md`, `memory/future-features.md`

---
> **Comment maintenir ce document** : retirer un item dès qu'il est fait (et l'ajouter à `memory/completed.md`). Ajouter un item dès qu'une tâche est explicitement reportée par l'utilisateur ("fais ça plus tard", "pas maintenant").
