# Purpose
Snapshot of "where things stand right now" — what's verified working, what's known-broken, as of the last update. This is the **first file to check** at the start of a new session.

# Summary
**As of 2026-06-30**: the app builds cleanly (`mvn clean install`) and runs cleanly (`mvn -pl tnt-bootstrap spring-boot:run`), verified end-to-end including Swagger UI, RBAC, and the two previously-flagged infra bugs (sync table, MinIO). No known blocking issues remain — only external (Kernel-side) gaps and non-blocking tech debt.

# Details

## Build & runtime status
| Check | Status |
|---|---|
| `mvn clean install` (full reactor) | ✅ passes |
| `mvn -pl tnt-bootstrap spring-boot:run` | ✅ starts cleanly, `✅ COMPLETED`, 30 modules active |
| `/swagger-ui.html` | ✅ returns 200, full OpenAPI doc generates |
| `@RequirePermission` / RBAC | ✅ fully functional (LOCAL mode), see `security/permissions.md` |
| `tnt_sync_session` and related tables | ✅ created, scheduler runs without error |
| MinIO incident evidence archival | ✅ correct bucket logic, correct credentials |

## Known-non-blocking gaps (expected, not bugs)
- Kernel `POST /v1/roles` → `404` (Kernel hasn't implemented this endpoint yet)
- Kernel `GET /v1/permissions/resolve` → not callable (same reason; irrelevant while `tnt.roles.permission.mode=LOCAL`, the default)
- See `development/roadmap.md` for the full list.

## What changed most recently (reverse chronological — see `memory/completed.md` for full history)
1. **2026-06-30**: full `docs/` "second brain" documentation tree created (this file is part of it).
2. **2026-06-30**: `tnt_sync_session` Liquibase wiring fix; `IncidentMediaStorageAdapter` tenant-bucket fix; MinIO default-profile credential fix.
3. **2026-06-30**: springdoc 2.5.0→3.0.3, Lombok 1.18.32→1.18.46, Kernel transitive swagger-jar exclusion (Spring Boot 4 compatibility chain).
4. **2026-06-29**: `@RequirePermission` hybrid LOCAL/REMOTE/HYBRID resolver architecture implemented (ADR-004).
5. **(earlier)**: Liquibase `includeAll`→explicit `include:` cleanup across ~20 modules; Lombok+MapStruct annotation-processor binding fix.

## Environment notes (don't re-derive these)
- Dev infra: `docker-compose.yml` in `tnt-bootstrap/` — postgres/redis/kafka/minio/elasticsearch/prometheus/grafana/zipkin, see `infrastructure/docker.md`.
- JDK in use: 25.0.1 (via sdkman `current` symlink) — Lombok version is pinned partly because of this, see `knowledge/known-issues.md` #4.
- No staging/prod environment has been touched in this session — all verification was local dev profile.

# Links
- `memory/completed.md` — full work log
- `memory/known-problems.md` — what's still open
- `knowledge/known-issues.md` — incident write-ups

---
> **Comment maintenir ce document** : mettre à jour la section "Build & runtime status" et "What changed most recently" à la fin de CHAQUE session de travail significative. C'est le document qui évite de re-vérifier des choses déjà vérifiées.
