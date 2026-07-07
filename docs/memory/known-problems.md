# Purpose
What's currently broken or degraded, right now — as opposed to `knowledge/known-issues.md`, which is the historical incident log (including resolved issues).

# Summary
No known *blocking* problems as of 2026-06-30. Two external (Kernel-side) gaps produce expected log noise but don't affect functionality in the default configuration.

# Details

## Currently active (non-blocking)
| Problem | Symptom | Impact | Tracked in |
|---|---|---|---|
| Kernel role-provisioning endpoint missing | `404 Not Found from POST .../v1/roles` at every startup | None — provisioning is best-effort/idempotent, app functions fully without it | `development/roadmap.md` |
| Kernel permission-resolution endpoint missing | N/A (not called while `tnt.roles.permission.mode=LOCAL`) | None today; would matter if mode is switched to `REMOTE`/`HYBRID` | `security/permissions.md` |

## Recently resolved (kept here briefly for "is this still broken?" lookups — full write-up in `knowledge/known-issues.md`)
| Problem | Resolved |
|---|---|
| `tnt_sync_session` table missing | 2026-06-30 |
| MinIO `SignatureDoesNotMatch` on incident evidence | 2026-06-30 |
| `IncidentMediaStorageAdapter` wrong-bucket logic | 2026-06-30 |
| `/swagger-ui.html` infinite spin | 2026-06-30 |
| `/v3/api-docs` `NoSuchMethodError` | 2026-06-30 |
| Lombok/JDK25 compile failure | 2026-06-30 |
| `@RequirePermission` missing bean in default profile | 2026-06-29 |

# Links
- `knowledge/known-issues.md` — full incident write-ups with root cause
- `memory/current-state.md` — overall health snapshot
- `memory/todo.md` — actionable next steps

---
> **Comment maintenir ce document** : ajouter une ligne à "Currently active" dès qu'un problème réel est découvert et non résolu en fin de session. Déplacer vers "Recently resolved" dès la résolution, puis retirer complètement après ~1-2 sessions (l'historique complet reste dans `knowledge/known-issues.md`).
